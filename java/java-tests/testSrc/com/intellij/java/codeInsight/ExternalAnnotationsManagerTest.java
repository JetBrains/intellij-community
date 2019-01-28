// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.xml.util.XmlUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.jetbrains.idea.eclipse.util.PathUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalAnnotationsManagerTest extends LightPlatformTestCase {
  private final DefaultLightProjectDescriptor myDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      Sdk jdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      Sdk sdk = PsiTestUtil.addJdkAnnotations(jdk);
      String home = jdk.getHomeDirectory().getParent().getPath();
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), home);
      String toolsPath = home + "/lib/tools.jar!/";
      VirtualFile toolsJar = JarFileSystem.getInstance().findFileByPath(toolsPath);

      Sdk plusTools = PsiTestUtil.addRootsToJdk(sdk, OrderRootType.CLASSES, toolsJar);

      Collection<String> utilClassPath = PathManager.getUtilClassPath();
      VirtualFile[] files = StreamEx.of(utilClassPath)
        .append(PathManager.getJarPathForClass(Range.class))
        .map(path -> path.endsWith(".jar") ?
                     JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path) + "!/") :
                     LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path)))
        .toArray(VirtualFile[]::new);

      return PsiTestUtil.addRootsToJdk(plusTools, OrderRootType.CLASSES, files);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor;
  }

  public void testBundledAnnotationXmlSyntax() {
    String root = PathManagerEx.getCommunityHomePath() + "/java/jdkAnnotations";
    findAnnotationsXmlAndCheckSyntax(root);
  }

  private static void findAnnotationsXmlAndCheckSyntax(String root) {
    VirtualFile jdkAnnoRoot = LocalFileSystem.getInstance().findFileByPath(root);
    VfsUtilCore.visitChildrenRecursively(
      jdkAnnoRoot, new VirtualFileVisitor() {
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

  private static void checkSyntax(@NotNull VirtualFile file, @NotNull String assumedPackage) {
    //System.out.println("file = " + file);
    ExternalAnnotationsManagerImpl manager = (ExternalAnnotationsManagerImpl)ExternalAnnotationsManager.getInstance(getProject());
    PsiFile psiFile = getPsiManager().findFile(file);
    MostlySingularMultiMap<String, BaseExternalAnnotationsManager.AnnotationData> map = manager.getDataFromFile(psiFile);
    for (String externalName : map.keySet()) {
      checkExternalName(psiFile, externalName, assumedPackage);

      // 'annotation name="org.jetbrains.annotations.NotNull"' should have FQN
      for (BaseExternalAnnotationsManager.AnnotationData annotationData : map.get(externalName)) {
        PsiAnnotation annotation = annotationData.getAnnotation(manager);
        String nameText = annotation.getNameReferenceElement().getText();
        assertClassFqn(nameText, psiFile, externalName, null);
      }
    }
  }

  private static PsiClass assertClassFqn(@NotNull String text,
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

  private static void checkExternalName(@NotNull PsiFile psiFile, @NotNull String externalName, @NotNull String assumedPackage) {
    // 'item name="java.lang.ClassLoader java.net.URL getResource(java.lang.String) 0"' should have all FQNs
    String unescaped = StringUtil.unescapeXmlEntities(externalName);
    List<String> words = StringUtil.split(unescaped, " ");
    String className = words.get(0);
    PsiClass aClass = assertClassFqn(className, psiFile, externalName, assumedPackage);
    if (words.size() == 1) return;

    String rest = unescaped.substring(className.length() + " ".length());

    if (rest.indexOf('(') == -1) {
      // field
      String field = StringUtil.trim(rest);
      PsiField psiField = aClass.findFieldByName(field, false);
      if (psiField == null) {
        fail("Field '"+field+"' not found in class '"+aClass.getQualifiedName()+"'", psiFile, externalName);
      }
      return;
    }
    String methodName = ContainerUtil.getLastItem(StringUtil.getWordsIn(rest.substring(0, rest.indexOf('('))));

    String methodSignature = rest.substring(0, rest.indexOf(')') + 1);
    String methodExternalName = className + " " + methodSignature;

    List<PsiMethod> methods = Arrays.stream(aClass.getMethods())
      .filter(method -> methodExternalName.equals(PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)))
      .collect(Collectors.toList());
    boolean found = !methods.isEmpty();
    if (!found) {
      List<String> candidates = ContainerUtil.map(aClass.findMethodsByName(methodName, false), method ->
        XmlUtil.escape(PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)));
      String additionalMsg = candidates.isEmpty() ? "" : "\nMaybe you have meant one of these methods instead:\n"+StringUtil.join(candidates, "\n")+"\n";
      fail("This method was not found in class '"+aClass.getQualifiedName()+"':\n"+"'"+methodSignature+"'"+additionalMsg, psiFile, externalName);
    }

    String parameterNumberText = StringUtil.trim(rest.substring(rest.indexOf(')') + 1));
    if (parameterNumberText.isEmpty()) return;

    try {
      int paramNumber = Integer.parseInt(parameterNumberText);
      PsiMethod method = methods.get(0);
      if (method.getParameterList().getParametersCount() <= paramNumber) {
        fail("Parameter number '"+paramNumber+"' is too big for a method '"+methodSignature+"'", psiFile, externalName);
      }
    }
    catch (NumberFormatException e) {
      fail("Parameter number is not an integer: '"+parameterNumberText+"'", psiFile, externalName);
    }
  }
}
