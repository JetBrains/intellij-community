/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ToolExtensionPoints;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class Java15APIUsageInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public static final String SHORT_NAME = "Since15";
  public static final ExtensionPointName<FileCheckingInspection> EP_NAME =
    ExtensionPointName.create(ToolExtensionPoints.JAVA15_INSPECTION_TOOL);

  private static final String EFFECTIVE_LL = "effectiveLL";

  private static final Map<LanguageLevel, Reference<Set<String>>> ourForbiddenAPI = ContainerUtil.newEnumMap(LanguageLevel.class);
  private static final Set<String> ourIgnored16ClassesAPI = new THashSet<String>(10);
  private static final Map<LanguageLevel, String> ourPresentableShortMessage = ContainerUtil.newEnumMap(LanguageLevel.class);
  static {
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_3, "1.4");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_4, "1.5");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_5, "1.6");
    ourPresentableShortMessage.put(LanguageLevel.JDK_1_6, "1.7");

    loadForbiddenApi("ignore16List.txt", ourIgnored16ClassesAPI);
  }

  private static Set<String> ourGenerifiedClasses = new HashSet<String>();
  static {
    ourGenerifiedClasses.add("javax.swing.JComboBox");
    ourGenerifiedClasses.add("javax.swing.ListModel");
  }

  protected LanguageLevel myEffectiveLanguageLevel = null;

  @Nullable
  private static Set<String> getForbiddenApi(@NotNull LanguageLevel languageLevel) {
    if (!ourPresentableShortMessage.containsKey(languageLevel)) return null;
    Reference<Set<String>> ref = ourForbiddenAPI.get(languageLevel);
    Set<String> result;
    if (ref == null || (result = ref.get()) == null) {
      result = new THashSet<String>(1000);
      loadForbiddenApi("api" + getShortName(languageLevel) + ".txt", result);
      ourForbiddenAPI.put(languageLevel, new SoftReference<Set<String>>(result));
    }
    return result;
  }

  private static void loadForbiddenApi(@NonNls String fileName, Set<String> set) {
    try {
      Class<?> aClass = Java15APIUsageInspectionBase.class;
      BufferedReader reader = new BufferedReader(new InputStreamReader(aClass.getResourceAsStream(fileName), CharsetToolkit.UTF8_CHARSET));
      try {
        do {
          String line = reader.readLine();
          if (line == null) break;
          set.add(line);
        } while(true);
      }
      finally {
        reader.close();
      }
    }
    catch (UnsupportedEncodingException ignored) { }
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
  public boolean isEnabledByDefault() {
    return false;
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
    private final ExtensionPoint<FileCheckingInspection> point = Extensions.getRootArea().getExtensionPoint(EP_NAME);

    public MyVisitor(final ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    @Override public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(reference.getElement());
        if (module != null) {
          final LanguageLevel languageLevel = getEffectiveLanguageLevel(module);
          if (isForbiddenApiUsage((PsiMember)resolved, languageLevel)) {
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
            registerError(reference, languageLevel);
          } else if (resolved instanceof PsiClass && isInProject(reference)&& !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiReferenceParameterList parameterList = reference.getParameterList();
            if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
              for (String generifiedClass : ourGenerifiedClasses) {
                if (InheritanceUtil.isInheritor((PsiClass)resolved, generifiedClass)) {
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
          if (isForbiddenApiUsage(constructor, languageLevel)) {
            registerError(expression.getClassReference(), languageLevel);
          }
        }
      }
    }

    private LanguageLevel getEffectiveLanguageLevel(Module module) {
      if (myEffectiveLanguageLevel != null) return myEffectiveLanguageLevel;
      return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    private void registerError(PsiJavaCodeReferenceElement reference, LanguageLevel api) {
      if (reference != null && isInProject(reference)) {
        myHolder.registerProblem(reference, InspectionsBundle.message("inspection.1.5.problem.descriptor", getShortName(api)));
      }
    }

    @Override
    public void visitFile(PsiFile file) {
      for (FileCheckingInspection inspection : point.getExtensions()) {
        ProblemDescriptor[] descriptors = inspection.checkFile(file, InspectionManager.getInstance(file.getProject()), myOnTheFly);
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            myHolder.registerProblem(descriptor);
          }
        }
      }
    }
  }

  private static String getJdkName(LanguageLevel languageLevel) {
    final String presentableText = languageLevel.getPresentableText();
    return presentableText.substring(0, presentableText.indexOf(" "));
  }

  public static boolean isForbiddenApiUsage(@NotNull PsiMember member, @NotNull LanguageLevel languageLevel) {
    if (member instanceof PsiAnonymousClass) return false;
    PsiClass containingClass = member.getContainingClass();
    if (containingClass instanceof PsiAnonymousClass) return false;
    if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return false;

    return isForbiddenSignature(member, languageLevel) ||
           containingClass != null && isForbiddenApiUsage(containingClass, languageLevel);

  }

  private static boolean isForbiddenSignature(@NotNull PsiMember member, @NotNull LanguageLevel languageLevel) {
    Set<String> forbiddenApi = getForbiddenApi(languageLevel);
    String signature = getSignature(member);
    return forbiddenApi != null && signature != null && isForbiddenSignature(signature, languageLevel, forbiddenApi);
  }

  private static boolean isForbiddenSignature(@NotNull String signature, @NotNull LanguageLevel languageLevel, @NotNull Set<String> forbiddenApi) {
    if (forbiddenApi.contains(signature)) {
      return true;
    }
    if (languageLevel.compareTo(LanguageLevel.HIGHEST) == 0) {
      return false;
    }
    LanguageLevel nextLanguageLevel = LanguageLevel.values()[languageLevel.ordinal() + 1];
    Set<String> nextForbiddenApi = getForbiddenApi(nextLanguageLevel);
    return nextForbiddenApi != null && isForbiddenSignature(signature, nextLanguageLevel, nextForbiddenApi);
  }

  @Nullable
  public static String getSignature(PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      return getSignature(member.getContainingClass()) + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      StringBuilder buf = new StringBuilder();
      buf.append(getSignature(method.getContainingClass()));
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      final PsiType[] params = method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes();
      for (PsiType type : params) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    assert false;
    return null;
  }
}
