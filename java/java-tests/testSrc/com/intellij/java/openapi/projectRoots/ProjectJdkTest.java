// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.openapi.projectRoots;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import org.jdom.Element;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectJdkTest extends PlatformTestCase {
  public void testDoesntCrashOnJdkRootDisappearance() throws Exception {
    VirtualFile nDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDir("nroot", true));
    String nUrl = nDir.getUrl();
    ProjectJdkImpl jdk = WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<ProjectJdkImpl, Exception>)()->{
      ProjectJdkImpl myJdk = (ProjectJdkImpl)ProjectJdkTable.getInstance().createSdk("my", JavaSdk.getInstance());
      Element element = JDOMUtil.load("<jdk version=\"2\">\n" +
                                      "      <name value=\"1.8\" />\n" +
                                      "      <type value=\"JavaSDK\" />\n" +
                                      "      <version value=\"java version &quot;1.8.0_152-ea&quot;\" />\n" +
                                      "      <homePath value=\"I:/Java/jdk1.8\" />\n" +
                                      "      <roots>\n" +
                                      "        <classPath>\n" +
                                      "          <root type=\"composite\">\n" +
                                      "            <root url=\"" + nUrl + "\" type=\"simple\" />\n" +
                                      "          </root>\n" +
                                      "        </classPath>\n" +
                                      "      </roots>\n" +
                                      "      <additional />\n" +
                                      "    </jdk>\n"
      );
      myJdk.readExternal(element);
      return myJdk;
    });

    try {
      List<String> urls = Arrays.stream(jdk.getRoots(OrderRootType.CLASSES)).peek(v -> assertTrue(v.isValid())).map(VirtualFile::getUrl).collect(Collectors.toList());
      assertOrderedEquals(urls, nUrl);

      delete(nDir);
      assertFalse(nDir.isValid());

      urls = Arrays.stream(jdk.getRoots(OrderRootType.CLASSES)).peek(v -> assertTrue(v.isValid())).map(VirtualFile::getUrl).collect(Collectors.toList());
      assertEmpty(urls);
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(), ()->ProjectJdkTable.getInstance().removeJdk(jdk));
    }
  }
}
