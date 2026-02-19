// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ProjectJdkTest extends HeavyPlatformTestCase {

  @Test
  public void testDoesntCrashOnJdkRootDisappearance() throws Exception {
    VirtualFile nDir = getTempDir().createVirtualDir();
    String nUrl = nDir.getUrl();
    Sdk jdk = WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Sdk, Exception>)()->{
      SdkBridge myJdk = (SdkBridge)ProjectJdkTable.getInstance().createSdk("my", JavaSdk.getInstance());
      @Language("XML")
      String s = "<jdk version=\"2\">\n" +
                 "  <name value=\"1.8\" />\n" +
                 "  <type value=\"JavaSDK\" />\n" +
                 "  <version value=\"java version &quot;1.8.0_152-ea&quot;\" />\n" +
                 "  <homePath value=\"I:/Java/jdk1.8\" />\n" +
                 "  <roots>\n" +
                 "    <classPath>\n" +
                 "      <root type=\"composite\">\n" +
                 "        <root url=\"" + nUrl + "\" type=\"simple\" />\n" +
                 "      </root>\n" +
                 "    </classPath>\n" +
                 "  </roots>\n" +
                 "  <additional />\n" +
                 "</jdk>\n";
      Element element = JDOMUtil.load(s);
      myJdk.readExternal(element);
      return myJdk;
    });

    try {
      List<String> urls = Arrays.stream(jdk.getRootProvider().getFiles(OrderRootType.CLASSES)).peek(v -> assertTrue(v.isValid())).map(VirtualFile::getUrl).collect(Collectors.toList());
      assertOrderedEquals(urls, nUrl);

      delete(nDir);
      assertFalse(nDir.isValid());

      urls = Arrays.stream(jdk.getRootProvider().getFiles(OrderRootType.CLASSES)).peek(v -> assertTrue(v.isValid())).map(VirtualFile::getUrl).collect(Collectors.toList());
      assertEmpty(urls);
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(), ()->ProjectJdkTable.getInstance().removeJdk(jdk));
    }
  }

  @Test
  public void testJdkAnnotationsAttachedAutomaticallyOnJDKCreation() throws Exception {
    Sdk jdk = WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Sdk, Exception>)()->
      ProjectJdkTable.getInstance().createSdk("my", JavaSdk.getInstance()));
    ((SdkType)jdk.getSdkType()).setupSdkPaths(jdk);

    try {
      Runnable fix = MagicConstantInspection.getAttachAnnotationsJarFix(getProject());
      assertNull(fix);

      VirtualFile[] virtualFiles = jdk.getRootProvider().getFiles(AnnotationOrderRootType.getInstance());
      List<VirtualFile> annotations = Arrays.asList(virtualFiles);

      assertNotEmpty(annotations);

      VirtualFile internalAnnotationsPath = JavaSdkImpl.internalJdkAnnotationsPath(new ArrayList<>(), false);
      assertContainsElements(annotations, internalAnnotationsPath);
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(), ()->ProjectJdkTable.getInstance().removeJdk(jdk));
    }
  }
}
