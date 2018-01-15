/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
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

public class ExternalAnnotationsManagerTest extends IdeaTestCase {
  @Override
  protected Sdk getTestProjectJdk() {
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
                   JarFileSystem.getInstance() .findFileByPath(FileUtil.toSystemIndependentName(path) + "!/") :
                   LocalFileSystem.getInstance() .findFileByPath(FileUtil.toSystemIndependentName(path)))
      .toArray(VirtualFile[]::new);

    return PsiTestUtil.addRootsToJdk(plusTools, OrderRootType.CLASSES, files);
  }

  public void testBundledAnnotationXmlSyntax() {
    String root = PathManagerEx.getCommunityHomePath() + "/java/jdkAnnotations";
    findAnnotationsXmlAndCheckSyntax(root);
  }

  private void findAnnotationsXmlAndCheckSyntax(String root) {
    VirtualFile jdkAnnoRoot = LocalFileSystem.getInstance().findFileByPath(root);
    VfsUtilCore.visitChildrenRecursively(jdkAnnoRoot, new VirtualFileVisitor() {
                                           @Override
                                           public boolean visitFile(@NotNull VirtualFile file) {
                                             if (file.getName().equals("annotations.xml")) {
                                               String assumedPackage = PathUtil.getRelative(root, file.getParent().getPath()).replaceAll("/",".");
                                               checkSyntax(file, assumedPackage);
                                             }
                                             return true;
                                           }
                                         });
  }

  //  some android classes are missing in IDEA, e.g. android.support.annotation.NonNull
  public void _testAndroidAnnotationsXml() {
    VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(PathManagerEx.getCommunityHomePath() + "/android/android/lib");
    VirtualFile[] androidJars = Arrays.stream(lib.getChildren())
      .map(file -> file.getName().endsWith(".jar") ?
                   JarFileSystem.getInstance().getJarRootForLocalFile(file) :
                   file)
      .toArray(VirtualFile[]::new);

    ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(getProject())
      .setProjectSdk(PsiTestUtil.addRootsToJdk(getTestProjectJdk(), OrderRootType.CLASSES, androidJars)));

    String root = PathManagerEx.getCommunityHomePath() + "/android/android/annotations";
    findAnnotationsXmlAndCheckSyntax(root);
  }

  private void checkSyntax(@NotNull VirtualFile file, @NotNull String assumedPackage) {
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

  private PsiClass assertClassFqn(@NotNull String text, @NotNull PsiFile psiFile, @NotNull String externalName, @Nullable("null means can be any") String assumedPackage) {
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
  private static void fail(String error, PsiFile psiFile, String externalName) {
    int offset = psiFile.getText().indexOf(XmlUtil.escape(externalName));
    int line = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile).getLineNumber(offset);
    fail(error + "\nFile: " + psiFile.getVirtualFile().getPath() + ":" + (line+1) + " (offset: "+offset+")");
  }

  private void checkExternalName(@NotNull PsiFile psiFile, @NotNull String externalName, @NotNull String assumedPackage) {
    // 'item name="java.lang.ClassLoader java.net.URL getResource(java.lang.String) 0"' should have all FQNs
    String unescaped = StringUtil.unescapeXml(externalName);
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
      List<String> candidates = Arrays.stream(aClass.findMethodsByName(methodName, false))
        .map(method -> XmlUtil.escape(PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE)))
        .collect(Collectors.toList());
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
