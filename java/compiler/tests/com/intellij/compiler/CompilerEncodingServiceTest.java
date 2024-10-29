// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CompilerEncodingServiceTest extends JavaPsiTestCase {
  private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  private Collection<Charset> projectDefaultPlus(Charset @NotNull ... charsets) {
    Set<Charset> result = new HashSet<>();
    result.add(getProjectDefault());
    result.addAll(Arrays.asList(charsets));
    return result;
  }

  public void testDefaultEncoding() {
    assertSameElements(getService().getAllModuleEncodings(myModule), getProjectDefault());
    assertEquals(getProjectDefault(), getService().getPreferredModuleEncoding(myModule));
  }

  public void testJavaFileEncoding() {
    assertSameElements(getService().getAllModuleEncodings(myModule), getProjectDefault());
    final VirtualFile file = createFile("A.java");
    assertEquals(getProjectDefault(), file.getCharset());
    assertSameElements(getService().getAllModuleEncodings(myModule), getProjectDefault());
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);

    assertSameElements(getService().getAllModuleEncodings(myModule), projectDefaultPlus(WINDOWS_1251));
  }

  public void testPropertiesEncodingTest() {
    final VirtualFile file = createFile("A.properties");
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);

    assertSameElements(getService().getAllModuleEncodings(myModule), getProjectDefault());
  }

  @SuppressWarnings({"TextBlockMigration", "NonAsciiCharacters"})
  public void testPropertiesAutoEncoding() throws IOException {
    //
    final Ref<byte[]> content = Ref.create();
    final VirtualFile file = createFile("test.properties");
    WriteAction.run(() -> {
      content.set(("one=1\n" +
                   "two=2\n").getBytes(StandardCharsets.ISO_8859_1));
      file.setBinaryContent(content.get());
    });
    file.setCharset(null);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    //
    WriteAction.run(() -> {
      content.set(ArrayUtil.mergeArrays(content.get(), ("three=3️⃣\n" +
                                                        "four=4️⃣\n").getBytes(StandardCharsets.UTF_8)));
      file.setBinaryContent(content.get());
    });
    file.setCharset(null);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    //
    WriteAction.run(() -> {
      content.set(ArrayUtil.mergeArrays(content.get(), ("five=fünf\n" +
                                                        "six=sechs\n").getBytes(StandardCharsets.ISO_8859_1)));

      file.setBinaryContent(content.get());
    });
    file.setCharset(null);
    assertEquals(StandardCharsets.ISO_8859_1, file.getCharset());
  }

  public void testBigPropertiesAutoEncoding() throws IOException {
    final VirtualFile file = createFile("test.properties");

    WriteAction.run(() -> {
      @SuppressWarnings("NonAsciiCharacters")
      byte[] bytes = "verifyEmail.tooltip=初回ログイン後またはアドレスの変更が送信された後に、ユーザーに自分の電子メールアドレスを確認するように要求します。\n"
        .repeat(64).getBytes(StandardCharsets.UTF_8);
      file.setBinaryContent(bytes);
    });

    LoadTextUtil.loadText(file, 1024);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

  }


  public void testPropertiesEncodingFeatureFlagTest() {
    RegistryValue registryValue = Registry.get("properties.file.encoding.legacy.support");
    try {
      registryValue.setValue(true);
      final VirtualFile file = createFile("A.properties");
      assertEquals(StandardCharsets.ISO_8859_1, file.getCharset());
      EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);

      assertSameElements(getService().getAllModuleEncodings(myModule), getProjectDefault());
    }
    finally {
      registryValue.resetToDefault();
    }
  }


  public void testTwoJavaFilesWithDifferentEncodings() {
    final VirtualFile fileA = createFile("A.java");
    final VirtualFile fileB = createFile("B.java");
    assertEquals(getProjectDefault(), fileA.getCharset());
    assertEquals(getProjectDefault(), fileB.getCharset());
    EncodingProjectManager.getInstance(myProject).setEncoding(fileA, WINDOWS_1251);
    EncodingProjectManager.getInstance(myProject).setEncoding(fileB, WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), projectDefaultPlus(WINDOWS_1251, WINDOWS_1252));
  }

  public void testJavaAndNonJavaFilesWithDifferentEncodings() {
    final VirtualFile fileA = createFile("A.java");
    final VirtualFile fileB = createFile("B.properties");
    assertEquals(getProjectDefault(), fileA.getCharset());
    assertEquals(StandardCharsets.UTF_8, fileB.getCharset());
    EncodingProjectManager.getInstance(myProject).setEncoding(fileA, WINDOWS_1251);
    EncodingProjectManager.getInstance(myProject).setEncoding(fileB, WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), projectDefaultPlus(WINDOWS_1251));
  }

  public void testJavaAndNonJavaFilesWithDifferentEncodingsFeatureFlag() {
    RegistryValue registryValue = Registry.get("properties.file.encoding.legacy.support");
    try {
      registryValue.setValue(true);

      final VirtualFile fileA = createFile("A.java");
      final VirtualFile fileB = createFile("B.properties");
      assertEquals(getProjectDefault(), fileA.getCharset());
      assertEquals(StandardCharsets.ISO_8859_1, fileB.getCharset());
      EncodingProjectManager.getInstance(myProject).setEncoding(fileA, WINDOWS_1251);
      EncodingProjectManager.getInstance(myProject).setEncoding(fileB, WINDOWS_1252);

      assertSameElements(getService().getAllModuleEncodings(myModule), projectDefaultPlus(WINDOWS_1251));
    }
    finally {
      registryValue.resetToDefault();
    }
  }

  public void testSourceRootEncodingDominatesOnFileEncoding() {
    final VirtualFile file = createFile("A.java");
    assertEquals(getProjectDefault(), file.getCharset());
    EncodingProjectManager.getInstance(myProject).setEncoding(file, WINDOWS_1251);
    assertSameElements(getService().getAllModuleEncodings(myModule), projectDefaultPlus(WINDOWS_1251));
    assertEquals(WINDOWS_1251, getService().getPreferredModuleEncoding(myModule));

    EncodingProjectManager.getInstance(myProject).setEncoding(file.getParent(), WINDOWS_1252);

    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251, WINDOWS_1252);
    assertEquals(WINDOWS_1252, getService().getPreferredModuleEncoding(myModule));
  }

  public void testUseContentRootEncodingIfEncodingForSourceRootIsNotSpecified() throws IOException {
    VirtualFile contentRoot = getVirtualFile(createTempDir("contentRoot"));
    PsiTestUtil.addContentRoot(myModule, contentRoot);
    VirtualFile srcDir = createChildDirectory(contentRoot, "src");
    PsiTestUtil.addSourceRoot(myModule, srcDir);
    EncodingProjectManager.getInstance(myProject).setEncoding(contentRoot, WINDOWS_1251);
    assertSameElements(getService().getAllModuleEncodings(myModule), WINDOWS_1251);
  }

  private VirtualFile createFile(final String fileName) {
    try {
      final VirtualFile file = createFile(fileName, "").getVirtualFile();
      assertNotNull(file);
      return file;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private Charset getProjectDefault() {
    return EncodingProjectManager.getInstance(getProject()).getDefaultCharset();
  }

  private CompilerEncodingService getService() {
    return CompilerEncodingService.getInstance(myProject);
  }
}
