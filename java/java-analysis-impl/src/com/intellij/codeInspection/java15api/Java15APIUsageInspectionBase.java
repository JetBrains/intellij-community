/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.java15api;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class Java15APIUsageInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public static final String SHORT_NAME = "Since15";

  private static final String EFFECTIVE_LL = "effectiveLL";

  private static final Map<LanguageLevel, Reference<Set<String>>> ourForbiddenAPI = ContainerUtil.newEnumMap(LanguageLevel.class);
  private static final Set<String> ourIgnored16ClassesAPI = new THashSet<>(10);
  private static final Map<LanguageLevel, String> ourPresentableShortMessage = ContainerUtil.newEnumMap(LanguageLevel.class);

  private static final LanguageLevel ourHighestKnownLanguage = LanguageLevel.JDK_1_9;

  static {
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_3, "1.4");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_4, "1.5");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_5, "1.6");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_6, "1.7");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_7, "1.8");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_8, "1.9");

    loadForbiddenApi("ignore16List.txt", ourIgnored16ClassesAPI);
  }

  private static final Set<String> ourGenerifiedClasses = new HashSet<>();
  static {
    ourGenerifiedClasses.add("javax.swing.JComboBox");
    ourGenerifiedClasses.add("javax.swing.ListModel");
    ourGenerifiedClasses.add("javax.swing.JList");
  }
  
  private static final Set<String> ourDefaultMethods = new HashSet<>();
  static {
    ourDefaultMethods.add("java.util.Iterator#remove()");
  }

  LanguageLevel myEffectiveLanguageLevel;

  @Nullable
  private static Set<String> getForbiddenApi(@NotNull LanguageLevel languageLevel) {
    if (!ourPresentableShortMessage.containsKey(languageLevel)) return null;
    Reference<Set<String>> ref = ourForbiddenAPI.get(languageLevel);
    Set<String> result = SoftReference.dereference(ref);
    if (result == null) {
      result = new THashSet<>(1000);
      loadForbiddenApi("api" + getShortName(languageLevel) + ".txt", result);
      ourForbiddenAPI.put(languageLevel, new SoftReference<>(result));
    }
    return result;
  }

  private static void loadForbiddenApi(String fileName, Set<String> set) {
    URL resource = Java15APIUsageInspectionBase.class.getResource(fileName);
    if (resource == null) {
      Logger.getInstance(Java15APIUsageInspectionBase.class).warn("not found: " + fileName);
      return;
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), CharsetToolkit.UTF8_CHARSET))) {
      set.addAll(FileUtil.loadLines(reader));
    }
    catch (IOException ignored) { }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.1.5.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }


  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    final Element element = node.getChild(EFFECTIVE_LL);
    if (element != null) {
      myEffectiveLanguageLevel = LanguageLevel.valueOf(element.getAttributeValue("value"));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (myEffectiveLanguageLevel != null) {
      final Element llElement = new Element(EFFECTIVE_LL);
      llElement.setAttribute("value", myEffectiveLanguageLevel.toString());
      node.addContent(llElement);
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder, isOnTheFly);
  }

  private static boolean isInProject(final PsiElement elt) {
    return elt.getManager().isInProject(elt);
  }

  public static String getShortName(LanguageLevel languageLevel) {
    return ourPresentableShortMessage.get(languageLevel);
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;

    public MyVisitor(final ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    @Override public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(aClass instanceof PsiTypeParameter)) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
        final LanguageLevel effectiveLanguageLevel = module != null ? getEffectiveLanguageLevel(module) : null;
        if (effectiveLanguageLevel != null && !effectiveLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          final JavaSdkVersion version = JavaVersionService.getInstance().getJavaSdkVersion(aClass);
          if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_8)) {
            final List<PsiMethod> methods = new ArrayList<>();
            for (HierarchicalMethodSignature methodSignature : aClass.getVisibleSignatures()) {
              final PsiMethod method = methodSignature.getMethod();
              if (ourDefaultMethods.contains(getSignature(method))) {
                methods.add(method);
              }
            }
  
            if (!methods.isEmpty()) {
              PsiElement element2Highlight = aClass.getNameIdentifier();
              if (element2Highlight == null) {
                element2Highlight = aClass;
              }
              myHolder.registerProblem(element2Highlight,
                                       methods.size() == 1 ? InspectionsBundle.message("inspection.1.8.problem.single.descriptor", methods.get(0).getName(), getJdkName(effectiveLanguageLevel)) 
                                                           : InspectionsBundle.message("inspection.1.8.problem.descriptor", methods.size(), getJdkName(effectiveLanguageLevel)),
                                       QuickFixFactory.getInstance().createImplementMethodsFix(aClass));
            }
          }
        }
      }
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitNameValuePair(PsiNameValuePair pair) {
      super.visitNameValuePair(pair);
      PsiReference reference = pair.getReference();
      if (reference != null) {
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiCompiledElement && resolve instanceof PsiAnnotationMethod) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(pair);
          if (module != null) {
            final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
            LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel((PsiMember)resolve, languageLevel);
            if (sinceLanguageLevel != null) {
              registerError(ObjectUtils.notNull(pair.getNameIdentifier(), pair), sinceLanguageLevel);
            }
          }
        }
      }
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(reference.getElement());
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel((PsiMember)resolved, languageLevel);
          if (sinceLanguageLevel != null) {
            PsiClass psiClass = null;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier != null) {
              if (qualifier instanceof PsiExpression) {
                psiClass = PsiUtil.resolveClassInType(((PsiExpression)qualifier).getType());
              }
            }
            else {
              psiClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
            }
            if (psiClass != null) {
              if (isIgnored(psiClass)) return;
              for (PsiClass superClass : psiClass.getSupers()) {
                if (isIgnored(superClass)) return;
              }
            }
            registerError(reference, sinceLanguageLevel);
          } else if (resolved instanceof PsiClass && isInProject(reference)&& !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiReferenceParameterList parameterList = reference.getParameterList();
            if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
              for (String generifiedClass : ourGenerifiedClasses) {
                if (InheritanceUtil.isInheritor((PsiClass)resolved, generifiedClass) && 
                    !isRawInheritance(generifiedClass, (PsiClass)resolved, new HashSet<>())) {
                  String message = InspectionsBundle.message("inspection.1.7.problem.descriptor", getJdkName(languageLevel));
                  myHolder.registerProblem(reference, message);
                  break;
                }
              }
            }
          }
        }
      }
    }

    private boolean isRawInheritance(String generifiedClassQName, PsiClass currentClass, Set<PsiClass> visited) {
      for (PsiClassType classType : currentClass.getSuperTypes()) {
        if (classType.isRaw()) {
          return true;
        }
        final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
        final PsiClass superClass = resolveResult.getElement();
        if (visited.add(superClass) && InheritanceUtil.isInheritor(superClass, generifiedClassQName)) {
          if (isRawInheritance(generifiedClassQName, superClass, visited)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isIgnored(PsiClass psiClass) {
      final String qualifiedName = psiClass.getQualifiedName();
      return qualifiedName != null && ourIgnored16ClassesAPI.contains(qualifiedName);
    }

    @Override public void visitNewExpression(final PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      final Module module = ModuleUtilCore.findModuleForPsiElement(expression);
      if (module != null) {
        final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
        if (constructor instanceof PsiCompiledElement) {
          LanguageLevel sinceLanguageLevel = getLastIncompatibleLanguageLevel(constructor, languageLevel);
          if (sinceLanguageLevel != null) {
            registerError(expression.getClassReference(), sinceLanguageLevel);
          }
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      PsiAnnotation annotation = !method.isConstructor() ? AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_OVERRIDE) : null;
      if (annotation != null) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
        LanguageLevel sinceLanguageLevel = null;
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          final PsiMethod[] methods = method.findSuperMethods();
          for (PsiMethod superMethod : methods) {
            if (superMethod instanceof PsiCompiledElement) {
              sinceLanguageLevel = getLastIncompatibleLanguageLevel(superMethod, languageLevel);
              if (sinceLanguageLevel == null) {
                return;
              }
            }
            else {
              return;
            }
          }
          if (methods.length > 0) {
            registerError(annotation.getNameReferenceElement(), sinceLanguageLevel);
          }
        }
      }
    }

    private LanguageLevel getEffectiveLanguageLevel(Module module) {
      if (myEffectiveLanguageLevel != null) return myEffectiveLanguageLevel;
      return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    private void registerError(PsiElement reference, LanguageLevel api) {
      if (reference != null && isInProject(reference)) {
        //noinspection DialogTitleCapitalization
        myHolder.registerProblem(reference,
                                 InspectionsBundle.message("inspection.1.5.problem.descriptor", getShortName(api)),
                                 myOnTheFly ? new LocalQuickFix[] {(LocalQuickFix)QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(LanguageLevel.values()[api.ordinal() + 1])} : null);
      }
    }
  }

  private static String getJdkName(LanguageLevel languageLevel) {
    final String presentableText = languageLevel.getPresentableText();
    return presentableText.substring(0, presentableText.indexOf(' '));
  }

  public static LanguageLevel getLastIncompatibleLanguageLevel(@NotNull PsiMember member, @NotNull LanguageLevel languageLevel) {
    if (member instanceof PsiAnonymousClass) return null;
    PsiClass containingClass = member.getContainingClass();
    if (containingClass instanceof PsiAnonymousClass) return null;
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return null;

    Set<String> forbiddenApi = getForbiddenApi(languageLevel);
    String signature = getSignature(member);
    if (forbiddenApi != null && signature != null) {
      LanguageLevel lastIncompatibleLanguageLevel = getLastIncompatibleLanguageLevelForSignature(signature, languageLevel, forbiddenApi);
      if (lastIncompatibleLanguageLevel != null) return lastIncompatibleLanguageLevel;
    }
    return containingClass != null ? getLastIncompatibleLanguageLevel(containingClass, languageLevel) : null;

  }

  private static LanguageLevel getLastIncompatibleLanguageLevelForSignature(@NotNull String signature, @NotNull LanguageLevel languageLevel, @NotNull Set<String> forbiddenApi) {
    if (forbiddenApi.contains(signature)) {
      return languageLevel;
    }
    if (languageLevel.compareTo(ourHighestKnownLanguage) == 0) {
      return null;
    }
    LanguageLevel nextLanguageLevel = LanguageLevel.values()[languageLevel.ordinal() + 1];
    Set<String> nextForbiddenApi = getForbiddenApi(nextLanguageLevel);
    return nextForbiddenApi != null ? getLastIncompatibleLanguageLevelForSignature(signature, nextLanguageLevel, nextForbiddenApi) : null;
  }

  /**
   * please leave public for JavaAPIUsagesInspectionTest#testCollectSinceApiUsages
   */
  @Nullable
  public static String getSignature(@Nullable PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      String containingClass = getSignature(member.getContainingClass());
      return containingClass == null ? null : containingClass + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      String containingClass = getSignature(member.getContainingClass());
      if (containingClass == null) return null;

      StringBuilder buf = new StringBuilder();
      buf.append(containingClass);
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      for (PsiType type : method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes()) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    return null;
  }
}
