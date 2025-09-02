// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver;
import com.intellij.openapi.application.ClassPathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.xml.util.XmlUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.util.PathUtil;

import javax.xml.bind.annotation.XmlElement;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ExternalAnnotationsManagerTest extends LightPlatformTestCase {
  private static final Set<String> KNOWN_EXCEPTIONS = Set.of(
    "java.util.stream.Stream<T> generate(java.util.function.Supplier<T>)", // replaced with Supplier<? extends T> in JDK11
    "java.nio.charset.Charset charset()" // in java.io.PrintStream since JDK18, but test runs on JDK17
  );

  private final DefaultLightProjectDescriptor myDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      //noinspection removal
      Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      Sdk sdk = PsiTestUtil.addJdkAnnotations(jdk);

      Collection<String> utilClassPath = ClassPathUtil.getUtilClassPath();
      VirtualFile[] files = StreamEx.of(utilClassPath)
        .append(PathManager.getJarPathForClass(XmlElement.class))
        .map(path -> path.endsWith(".jar") ?
                     JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path) + "!/") :
                     LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path)))
        .toArray(VirtualFile[]::new);

      return PsiTestUtil.addRootsToJdk(sdk, OrderRootType.CLASSES, files);
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      ExternalLibraryDescriptor descriptor = JetBrainsAnnotationsExternalLibraryResolver.getAnnotationsLibraryDescriptor(module);
      String coordinates =
        descriptor.getLibraryGroupId() + ":" + descriptor.getLibraryArtifactId() + ":" + descriptor.getPreferredVersion();
      MavenDependencyUtil.addFromMaven(model, coordinates);
    }
  };

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor;
  }

  public void testBundledAnnotationXmlSyntax() {
    findAnnotationsXmlAndCheckSyntax(PathManagerEx.getCommunityHomePath() + "/java/jdkAnnotations");
  }

  private void findAnnotationsXmlAndCheckSyntax(String root) {
    VirtualFile jdkAnnoRoot = LocalFileSystem.getInstance().findFileByPath(root);
    VfsUtilCore.visitChildrenRecursively(
      jdkAnnoRoot, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (file.getName().equals("annotations.xml")) {
            String assumedPackage = PathUtil.getRelative(root, file.getParent().getPath()).replaceAll("/", ".");
            checkSyntax(file, assumedPackage);
          }
          return true;
        }
      });
  }

  private void checkSyntax(@NotNull VirtualFile file, @NotNull String assumedPackage) {
    //System.out.println("file = " + file);
    ExternalAnnotationsManagerImpl manager = (ExternalAnnotationsManagerImpl)ExternalAnnotationsManager.getInstance(getProject());
    PsiFile psiFile = getPsiManager().findFile(file);
    MostlySingularMultiMap<String, BaseExternalAnnotationsManager.AnnotationData> map = manager.getDataFromFile(psiFile);
    for (String externalName : map.keySet()) {
      PsiModifierListOwner listOwner = checkExternalName(psiFile, externalName, assumedPackage);

      // 'annotation name="org.jetbrains.annotations.NotNull"' should have FQN
      for (BaseExternalAnnotationsManager.AnnotationData annotationData : map.get(externalName)) {
        PsiAnnotation annotation = annotationData.getAnnotation(manager);
        String nameText = annotation.getNameReferenceElement().getText();
        assertClassFqn(nameText, psiFile, externalName, null);

        String typePath = annotationData.getTypePath();
        if (typePath != null) {
          PsiType type;
          if (listOwner instanceof PsiVariable variable) {
            type = variable.getType();
          } else if (listOwner instanceof PsiMethod method) {
            type = method.getReturnType();
          } else {
            fail("typePath is not allowed for " + listOwner.getClass().getSimpleName(), psiFile, externalName);
            break;
          }
          String error = validatePath(typePath, type);
          if (error != null) {
            fail("Invalid typePath: " + error, psiFile, externalName);
          }
        }
        else {
          if (listOwner != null) {
            if ("org.intellij.lang.annotations.MagicConstant".equals(nameText)) {
              String typeText = getType(listOwner).getCanonicalText();
              assertTrue(externalName, "int".equals(typeText) || "long".equals(typeText) || "java.lang.String".equals(typeText));
            }
            else if ("org.jetbrains.annotations.Nullable".equals(nameText)
                     || "org.jetbrains.annotations.NotNull".equals(nameText)
                     || "org.jetbrains.annotations.UnknownNullability".equals(nameText)
                     || "org.intellij.lang.annotations.Flow".equals(nameText)) {
              assertFalse(externalName, getType(listOwner) instanceof PsiPrimitiveType);
            }
            else if ("org.jetbrains.annotations.Contract".equals(nameText)) {
              assertTrue(externalName, listOwner instanceof PsiMethod);
            }
            else if ("org.jetbrains.annotations.Range".equals(nameText)) {
              assertTrue(externalName, ClassUtils.isIntegral(getType(listOwner)));
            }
            else if ("org.jetbrains.annotations.NonNls".equals(nameText) || "org.jetbrains.annotations.Nls".equals(nameText)) {
              if (listOwner instanceof PsiClass
                  || listOwner instanceof PsiPackage
                  || "javax.swing.JComponent java.lang.Object getClientProperty(java.lang.Object) 0".equals(externalName)
                  || "javax.swing.ActionMap javax.swing.Action get(java.lang.Object) 0".equals(externalName)
                  || "javax.swing.ActionMap void put(java.lang.Object, javax.swing.Action) 0".equals(externalName)
                  || "javax.swing.JComponent void putClientProperty(java.lang.Object, java.lang.Object) 0".equals(externalName)
                  || "javax.swing.JComponent void putClientProperty(java.lang.Object, java.lang.Object) 1".equals(externalName)
                  || "javax.swing.InputMap void put(javax.swing.KeyStroke, java.lang.Object) 1".equals(externalName)) {
                // seems a little suspicious/weird but let it pass
                continue;
              }
              String typeText = getType(listOwner).getCanonicalText();
              assertTrue(externalName, "java.lang.String".equals(typeText)
                                       || "java.lang.String...".equals(typeText)
                                       || "java.lang.String[]".equals(typeText));
            }
            else if ("org.jetbrains.annotations.PropertyKey".equals(nameText)) {
              String typeText = getType(listOwner).getCanonicalText();
              assertEquals(externalName, "java.lang.String", typeText);
            }
            else if ("org.jetbrains.annotations.Unmodifiable".equals(nameText)
                     || "org.jetbrains.annotations.UnmodifiableView".equals(nameText)) {
              PsiType type = getType(listOwner);
              assertTrue(InheritanceUtil.isInheritor(type, "java.util.Collection") || InheritanceUtil.isInheritor(type, "java.util.Map"));
            }
            else {
              fail(externalName + " " + nameText);
            }
          }
        }
      }
    }
  }

  private static @NotNull PsiType getType(@NotNull PsiModifierListOwner listOwner) {
    return Objects.requireNonNull(PsiUtil.getTypeByPsiElement(listOwner), () -> String.valueOf(listOwner));
  }

  private static String validatePath(String pathString, PsiType type) {
    if (!pathString.startsWith("/")) {
      return "Must start with '/'";
    }
    String[] components = pathString.split("/", -1);
    // The first component is always empty
    for (int i = 1; i < components.length; i++) {
      String component = components[i];
      if (component.equals("[]")) {
        if (!(type instanceof PsiArrayType arrayType)) {
          return "Invalid path: " + pathString + "; [] is only allowed for array types; type is "+type.getCanonicalText();
        }
        type = arrayType.getComponentType();
        continue;
      }
      else if (component.equals("*")) {
        if (!(type instanceof PsiWildcardType wildcardType)) {
          return "Invalid path: " + pathString + "; * is only allowed for wildcard types; type is "+type.getCanonicalText();
        }
        type = wildcardType.getBound();
        continue;
      }
      else if (component.equals(".")) {
        // TODO: not used currently anyway
        continue;
      }
      try {
        int number = Integer.parseInt(component);
        if (number >= 1 && number <= 255) {
          if (!(type instanceof PsiClassType classType)) {
            return "Invalid path: " + pathString + "; only class types can have components; type is "+type.getCanonicalText();
          }
          PsiType[] parameters = classType.getParameters();
          if (number > parameters.length) {
            return "Invalid path: " + pathString + "; component " + number + " is out of bounds; type is "+type.getCanonicalText();
          }
          type = parameters[number - 1];
          continue;
        }
      }
      catch (NumberFormatException ignored) { }
      return "Invalid path: " + pathString + "; invalid component: " + component;
    }
    return null;
  }

  private PsiClass assertClassFqn(@NotNull String text,
                                         @NotNull PsiFile psiFile,
                                         @NotNull String externalName,
                                         @Nullable("null means can be any") String assumedPackage) {
    if (!PsiNameHelper.getInstance(getProject()).isQualifiedName(text) || !text.contains(".")) {
      fail("'" + text + "' doesn't seem like a FQN", psiFile, externalName);
    }

    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(text, GlobalSearchScope.allScope(getProject()));
    if (aClass == null) {
      fail("'" + text + "' doesn't resolve to a class", psiFile, externalName);
    }
    String packageName = ((PsiClassOwner)aClass.getContainingFile()).getPackageName();
    if (assumedPackage != null && !assumedPackage.equals(packageName)) {
      fail("Wrong package for class '"+text+"'. Expected: '"+assumedPackage+"' but was: '"+packageName+"'", psiFile, externalName);
    }
    return aClass;
  }

  @Contract("_,_,_-> fail")
  private static void fail(String error, PsiFile psiFile, @NotNull String externalName) {
    int offset = psiFile.getText().indexOf(XmlUtil.escape(externalName));
    int line = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile).getLineNumber(offset);
    fail(error + "\nFile: " + psiFile.getVirtualFile().getPath() + ":" + (line+1) + " (offset: "+offset+")");
  }

  private PsiModifierListOwner checkExternalName(@NotNull PsiFile psiFile, @NotNull String externalName, @NotNull String assumedPackage) {
    // 'item name="java.lang.ClassLoader java.net.URL getResource(java.lang.String) 0"' should have all FQNs
    String unescaped = StringUtil.unescapeXmlEntities(externalName);
    List<String> words = StringUtil.split(unescaped, " ");
    String className = words.get(0);
    if (words.size() == 1) {
      PsiPackage psiPackage = JavaPsiFacade.getInstance(getProject()).findPackage(className);
      if (psiPackage != null) {
        return psiPackage;
      }
    }
    PsiClass aClass = assertClassFqn(className, psiFile, externalName, assumedPackage);
    if (words.size() == 1) return aClass;

    String rest = unescaped.substring(className.length() + " ".length());

    if (rest.indexOf('(') == -1) {
      // field
      String field = StringUtil.trim(rest);
      PsiField psiField = aClass.findFieldByName(field, false);
      if (psiField == null) {
        fail("Field '"+field+"' not found in class '"+aClass.getQualifiedName()+"'", psiFile, externalName);
      }
      return psiField;
    }
    String methodName = ContainerUtil.getLastItem(StringUtil.getWordsIn(rest.substring(0, rest.indexOf('('))));

    String methodSignature = rest.substring(0, rest.indexOf(')') + 1);
    String methodExternalName = className + " " + methodSignature;

    List<PsiMethod> methods = ContainerUtil.filter(aClass.getMethods(), method -> methodExternalName.equals(
      PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)));
    if (methods.isEmpty()) {
      // Sometimes the method is overridden in later JDK versions, and inferred contract is not satisfactory,
      // thus having explicit subclass contract is desired. Thus we don't fail if the annotated method exists
      // in superclass only
      methods = ContainerUtil.filter(
        aClass.getAllMethods(),
        method -> (method.getContainingClass().getQualifiedName() + " " + methodSignature)
          .equals(PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)));
    }
    boolean found = !methods.isEmpty();
    if (!found) {
      if (KNOWN_EXCEPTIONS.contains(methodSignature)) return null;
      List<String> candidates = ContainerUtil.map(aClass.findMethodsByName(methodName, true), method ->
        XmlUtil.escape(PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)));
      String additionalMsg = candidates.isEmpty() ? "" : "\nMaybe you have meant one of these methods instead:\n"+StringUtil.join(candidates, "\n")+"\n";
      fail("This method was not found in class '"+aClass.getQualifiedName()+"':\n"+"'"+methodSignature+"'"+additionalMsg, psiFile, externalName);
      return null;
    }

    PsiMethod method = methods.get(0);
    String parameterNumberText = StringUtil.trim(rest.substring(rest.indexOf(')') + 1));
    if (parameterNumberText.isEmpty()) return method;

    try {
      int paramNumber = Integer.parseInt(parameterNumberText);
      PsiParameter parameter = method.getParameterList().getParameter(paramNumber);
      if (parameter == null) {
        fail("Parameter number '"+paramNumber+"' is too big for a method '"+methodSignature+"'", psiFile, externalName);
      }
      return parameter; 
    }
    catch (NumberFormatException e) {
      fail("Parameter number is not an integer: '"+parameterNumberText+"'", psiFile, externalName);
    }
    return null;
  }
}
