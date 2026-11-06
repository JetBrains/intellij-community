// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.core.CoreJavaCodeStyleManager;
import com.intellij.core.CoreJavaDirectoryService;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.CoreJavaPsiImplementationHelper;
import com.intellij.core.CoreLanguageLevelProjectExtension;
import com.intellij.core.CorePackageIndex;
import com.intellij.core.CorePsiPackageImplementationHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.DumbJavaModuleGraphHelper;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaModuleGraphHelper;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.serviceContainer.ContainerUtilKt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JetBrains Light moves between the light and the smart mode without a restart, and the Java plugin then overrides the open defaults
 * of the basic Java modules. A caller can keep a default from the light mode. A proxy forwards that default to the override.
 * The checks make sure that each default is in {@code proxied-services.list} and that the generator can make a proxy for each key.
 * {@link CoreJavaDirectoryServiceProxyTest} checks that the Core code reaches its default through a proxy.
 */
@TestApplication
@SuppressWarnings("KotlinInternalInJava")
public class JavaBasicPluginProxiedServicesTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  /** The open defaults of the basic Java modules that the Java plugin overrides. */
  private static final List<String> OVERRIDDEN_DEFAULT_KEYS = List.of(
    "com.intellij.openapi.projectRoots.JavaVersionService",
    "com.intellij.psi.JavaDirectoryService",
    "com.intellij.psi.impl.file.PsiPackageImplementationHelper",
    "com.intellij.openapi.roots.LanguageLevelProjectExtension",
    "com.intellij.psi.impl.file.impl.JavaFileManager",
    "com.intellij.psi.impl.JavaPsiImplementationHelper",
    "com.intellij.psi.codeStyle.JavaCodeStyleManager",
    "com.intellij.openapi.roots.PackageIndex",
    "com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService",
    "com.intellij.psi.JavaModuleGraphHelper"
  );

  @Test
  public void everyOverriddenDefaultOfTheBasicModulesIsListed() {
    assertThat(ContainerUtilKt.getProxiedServicesList()).containsAll(OVERRIDDEN_DEFAULT_KEYS);
  }

  @Test
  public void eachListedServiceIsProxied() {
    var defaults = coreDefaults(projectFixture.get());
    assertThat(defaults.keySet()).extracting(Class::getName).containsExactlyElementsOf(OVERRIDDEN_DEFAULT_KEYS);
    for (var entry : defaults.entrySet()) {
      Object proxy = proxyOf(entry.getKey(), entry.getValue());
      assertThat(proxy).isInstanceOf(entry.getKey()).isInstanceOf(com.intellij.serviceContainer.ServiceProxyInstrumentation.class);
      assertThat(proxy.getClass().getName()).startsWith(entry.getKey().getName() + "$Proxy");
    }
  }

  @Test
  public void aProxyForwardsAndResets() {
    var light = new CoreLanguageLevelProjectExtension();
    light.setLanguageLevel(LanguageLevel.JDK_1_8);
    var smart = new CoreLanguageLevelProjectExtension();
    smart.setLanguageLevel(LanguageLevel.JDK_17);
    var proxy = proxyOf(LanguageLevelProjectExtension.class, light);
    assertEquals(LanguageLevel.JDK_1_8, proxy.getLanguageLevel());

    setForwarding(proxy, smart);
    assertEquals(LanguageLevel.JDK_17, proxy.getLanguageLevel());

    setForwarding(proxy, null);
    assertEquals(LanguageLevel.JDK_1_8, proxy.getLanguageLevel());
  }

  private static <T> @NotNull T proxyOf(@NotNull Class<T> key, @NotNull Object delegate) {
    return com.intellij.serviceContainer.ServiceProxyGenerator.INSTANCE.createInstance(key, delegate);
  }

  /** Makes the proxy forward to {@code target}, or to its own delegate again for {@code null}, as the container does on a move. */
  private static void setForwarding(@NotNull Object proxy, @Nullable Object target) {
    var instrumentation = (com.intellij.serviceContainer.ServiceProxyInstrumentation)proxy;
    instrumentation.setForwarding(target == null ? null : LazyKt.lazyOf(target));
  }

  private static @NotNull Map<Class<?>, Object> coreDefaults(@NotNull Project project) {
    Map<Class<?>, Object> defaults = new LinkedHashMap<>();
    defaults.put(JavaVersionService.class, new JavaVersionService());
    defaults.put(JavaDirectoryService.class, new CoreJavaDirectoryService());
    defaults.put(PsiPackageImplementationHelper.class, new CorePsiPackageImplementationHelper());
    defaults.put(LanguageLevelProjectExtension.class, new CoreLanguageLevelProjectExtension());
    defaults.put(JavaFileManager.class, new CoreJavaFileManager(project));
    defaults.put(JavaPsiImplementationHelper.class, new CoreJavaPsiImplementationHelper(project));
    defaults.put(JavaCodeStyleManager.class, new CoreJavaCodeStyleManager());
    defaults.put(PackageIndex.class, new CorePackageIndex());
    defaults.put(InternalPersistentJavaLanguageLevelReaderService.class, new InternalPersistentJavaLanguageLevelReaderService.DefaultImpl());
    defaults.put(JavaModuleGraphHelper.class, new DumbJavaModuleGraphHelper());
    return defaults;
  }
}
