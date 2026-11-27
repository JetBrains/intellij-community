// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.projectRoots.impl;

import com.intellij.codeInspection.magicConstant.MagicConstantInspection;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkBridge;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProjectJdkTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  @TempDir
  Path myTempDir;

  @Test
  public void testDoesntCrashOnJdkRootDisappearance() throws Exception {
    VirtualFile nDir = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectory(myTempDir.resolve("n"))));
    String nUrl = nDir.getUrl();
    Sdk jdk = WriteCommandAction.runWriteCommandAction(projectFixture.get(), (ThrowableComputable<Sdk, Exception>)()->{
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
      assertThat(urls).containsExactly(nUrl);

      VfsTestUtil.deleteFile(nDir);
      assertFalse(nDir.isValid());

      urls = Arrays.stream(jdk.getRootProvider().getFiles(OrderRootType.CLASSES)).peek(v -> assertTrue(v.isValid())).map(VirtualFile::getUrl).collect(Collectors.toList());
      assertThat(urls).isEmpty();
    }
    finally {
      WriteCommandAction.runWriteCommandAction(projectFixture.get(), ()->ProjectJdkTable.getInstance().removeJdk(jdk));
    }
  }

  @Test
  public void testJdkAnnotationsAttachedAutomaticallyOnJDKCreation() throws Exception {
    Sdk jdk = WriteCommandAction.runWriteCommandAction(projectFixture.get(), (ThrowableComputable<Sdk, Exception>)()->
      ProjectJdkTable.getInstance().createSdk("my", JavaSdk.getInstance()));
    ((SdkType)jdk.getSdkType()).setupSdkPaths(jdk);

    try {
      Runnable fix = ReadAction.computeBlocking(() -> MagicConstantInspection.getAttachAnnotationsJarFix(projectFixture.get()));
      assertNull(fix);

      VirtualFile[] virtualFiles = jdk.getRootProvider().getFiles(AnnotationOrderRootType.getInstance());
      List<VirtualFile> annotations = Arrays.asList(virtualFiles);

      assertThat(annotations).isNotEmpty();

      VirtualFile internalAnnotationsPath = JavaSdkImpl.internalJdkAnnotationsPath(new ArrayList<>(), false);
      assertThat(annotations).contains(internalAnnotationsPath);
    }
    finally {
      WriteCommandAction.runWriteCommandAction(projectFixture.get(), ()->ProjectJdkTable.getInstance().removeJdk(jdk));
    }
  }
}
