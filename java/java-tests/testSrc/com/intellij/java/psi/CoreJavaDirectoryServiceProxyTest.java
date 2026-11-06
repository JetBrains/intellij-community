// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.core.CoreJavaDirectoryService;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.JavaOptionalServices;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.file.impl.JavaFileManagerImpl;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.disposableFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.sourceRootFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link CoreJavaDirectoryService} gets the package from {@link JavaFileManager} without a cast to {@link CoreJavaFileManager},
 * so that a service proxy can wrap the file manager.
 */
@TestApplication
@SuppressWarnings("KotlinInternalInJava")
public class CoreJavaDirectoryServiceProxyTest {
  private static final TestFixture<Project> projectFixture = projectFixture();
  private static final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, "proxied-services", null);

  private final TestFixture<PsiDirectory> sourceRootFixture = sourceRootFixture(moduleFixture);
  private final TestFixture<Disposable> disposableFixture = disposableFixture();

  @Test
  public void coreDirectoryServiceGetsThePackageThroughAProxiedFileManager() {
    var project = projectFixture.get();
    var sourceRoot = sourceRootFixture.get();
    var fileManager = new CoreJavaFileManager(project);
    fileManager.addToClasspath(sourceRoot.getVirtualFile().getParent());
    var proxy = com.intellij.serviceContainer.ServiceProxyGenerator.INSTANCE.createInstance(JavaFileManager.class, fileManager);
    ServiceContainerUtil.replaceService(project, JavaFileManager.class, proxy, disposableFixture.get());
    assertSame(proxy, JavaOptionalServices.getJavaFileManager(project));

    var psiPackage = new CoreJavaDirectoryService().getPackage(sourceRoot);
    assertNotNull(psiPackage);
    assertEquals(sourceRoot.getName(), psiPackage.getQualifiedName());
  }

  @Test
  public void coreDirectoryServiceReportsAFileManagerWithoutTheCoreClasspath() {
    var sourceRoot = sourceRootFixture.get();
    var error = LoggedErrorProcessor.executeAndReturnLoggedError(() -> assertNull(new CoreJavaDirectoryService().getPackage(sourceRoot)));
    assertThat(error.getMessage()).contains(JavaFileManagerImpl.class.getName());
  }
}
