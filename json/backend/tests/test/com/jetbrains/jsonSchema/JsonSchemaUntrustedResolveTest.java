// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TrustedProjectsTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Regression coverage for IJPL-249802: a {@code $schema}/{@code $ref} reference declared in a project file must not be
 * resolved, while the project is not yet trusted, when it either
 * <ul>
 *   <li>escapes the opened project as a local path (e.g. {@code ../outside/schema.json}) — otherwise the IDE reads a
 *       local file outside the project directory before the user grants trust, or</li>
 *   <li>points at a remote {@code http}/{@code https} URL — otherwise the IDE fetches an attacker-controlled URL
 *       before the user grants trust.</li>
 * </ul>
 * The local cases use real files on disk (under and next to the project base directory) because the guard intentionally
 * applies only to local files; the in-memory test file system would not exercise it.
 */
public class JsonSchemaUntrustedResolveTest extends BasePlatformTestCase {

  private static final String SCHEMA_TEXT = """
    {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "required": ["mustExist"],
      "properties": {
        "mustExist": { "type": "string" }
      }
    }
    """;

  private static final String DATA_TEXT_TEMPLATE = """
    {
      "$schema": "%s",
      "name": "this file is intentionally missing mustExist"
    }
    """;

  private static final String MAPPED_SCHEMA_ID = "https://schema.example.test/project-mapped.json";
  private static final String MAPPED_SCHEMA_TEXT = """
    {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "$id": "https://schema.example.test/project-mapped.json",
      "type": "object"
    }
    """;

  private final List<Path> myPathsToDelete = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TrustedProjectsTestUtil.enableTrustedProjectsCheck(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Reset the explicit trust state that the test may have set on the shared light project.
      TrustedProjects.setProjectTrusted(getProject(), true);
      JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).setState(Collections.emptyMap());
      JsonSchemaService.Impl.get(getProject()).reset();
      for (Path path : myPathsToDelete) {
        NioFiles.deleteRecursively(path);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testOutsideProjectSchemaIsNotResolvedWhenUntrusted() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);

    String reference = "../" + outsideDir.getFileName() + "/schema.json";
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());
    String schemaRealPath = schemaPath.toRealPath().toString();
    assertNull("Test precondition: the outside schema must not be in the VFS cache",
               LocalFileSystem.getInstance().findFileByPathIfCached(schemaRealPath));

    // Untrusted: the file outside the project must not be resolved (and therefore not read).
    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("A schema outside the project must not be resolved before the project is trusted",
               service.findSchemaFileByReference(reference, dataFile));
    assertNull("Resolving a forbidden schema must not add it to the VFS cache",
               LocalFileSystem.getInstance().findFileByPathIfCached(schemaRealPath));

    // Trusted: resolution keeps working exactly as before the fix.
    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(getProject(), true);
    assertEquals("A schema outside the project must resolve once the project is trusted",
                 schemaFile, service.findSchemaFileByReference(reference, dataFile));
  }

  public void testInProjectSymlinkToOutsideSchemaIsNotResolvedWhenUntrusted() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-symlink-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    Path schemaLink = Files.createSymbolicLink(baseDir.resolve("schema-link.json"), schemaPath);
    myPathsToDelete.add(schemaLink);
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(schemaLink.getFileName()));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());

    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("An in-project symlink to an outside schema must not be resolved before the project is trusted",
               service.findSchemaFileByReference(schemaLink.getFileName().toString(), dataFile));

    VirtualFile schemaFile = findLocalFile(schemaLink);
    TrustedProjects.setProjectTrusted(getProject(), true);
    assertEquals("A symlinked schema must resolve once the project is trusted",
                 schemaFile, service.findSchemaFileByReference(schemaLink.getFileName().toString(), dataFile));
  }

  public void testInProjectSchemaIsResolvedEvenWhenUntrusted() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path schemaDir = Files.createDirectories(baseDir.resolve("schemas"));
    myPathsToDelete.add(schemaDir);
    Path schemaPath = Files.writeString(schemaDir.resolve("schema.json"), SCHEMA_TEXT);

    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted("schemas/schema.json"));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    VirtualFile schemaFile = findLocalFile(schemaPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());

    // A relative reference that stays inside the project must keep resolving even in an untrusted project.
    TrustedProjects.setProjectTrusted(getProject(), false);
    assertEquals("An in-project schema must resolve regardless of project trust",
                 schemaFile, service.findSchemaFileByReference("schemas/schema.json", dataFile));
  }

  public void testExternalContentRootDoesNotExpandUntrustedProjectBoundary() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path externalRootPath = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-external-root"));
    myPathsToDelete.add(externalRootPath);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), externalRootPath.toString());
    Path schemaPath = Files.writeString(externalRootPath.resolve("schema.json"), SCHEMA_TEXT);
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(schemaPath.toUri()));
    myPathsToDelete.add(dataPath);

    VirtualFile externalRoot = findLocalFile(externalRootPath);
    PsiTestUtil.addContentRoot(getModule(), externalRoot);
    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());

    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("A project-declared external content root must not expand the untrusted project boundary",
               service.findSchemaFileByReference(schemaPath.toUri().toString(), dataFile));

    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(getProject(), true);
    assertEquals("The external content-root schema must resolve once the project is trusted",
                 schemaFile, service.findSchemaFileByReference(schemaPath.toUri().toString(), dataFile));
  }

  public void testProjectMappingOutsideProjectDoesNotBypassReferenceGuard() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-mapped-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), MAPPED_SCHEMA_TEXT);
    Path dataPath = Files.writeString(baseDir.resolve("mapped-data.json"), DATA_TEXT_TEMPLATE.formatted(MAPPED_SCHEMA_ID));
    myPathsToDelete.add(dataPath);

    UserDefinedJsonSchemaConfiguration.Item dataMapping =
      new UserDefinedJsonSchemaConfiguration.Item("mapped-data.json", JsonMappingKind.File);
    UserDefinedJsonSchemaConfiguration mapping =
      new UserDefinedJsonSchemaConfiguration("outside", JsonSchemaVersion.SCHEMA_7, schemaPath.toString(), false, List.of(dataMapping));
    JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).setState(Map.of(mapping.getName(), mapping));
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());
    service.reset();
    VirtualFile dataFile = findLocalFile(dataPath);
    PsiFile dataPsiFile = PsiManager.getInstance(getProject()).findFile(dataFile);
    assertNotNull(dataPsiFile);

    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("A project mapping outside the project must not resolve by its $id before trust is granted",
               service.findSchemaFileByReference(MAPPED_SCHEMA_ID, dataFile));
    assertNull("A project mapping outside the project must not be selected before trust is granted",
               service.getSchemaObject(dataPsiFile));

    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(getProject(), true);
    assertEquals("The mapped schema must resolve by its $id once the project is trusted",
                 schemaFile, service.findSchemaFileByReference(MAPPED_SCHEMA_ID, dataFile));
    assertNotNull("The mapped schema must be selected once the project is trusted", service.getSchemaObject(dataPsiFile));
  }

  public void testTrustChangeInvalidatesCachedUnresolvedSchema() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-cached-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    String reference = "../" + outsideDir.getFileName() + "/schema.json";
    Path dataPath = Files.writeString(baseDir.resolve("cached-data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    PsiFile dataPsiFile = PsiManager.getInstance(getProject()).findFile(dataFile);
    assertNotNull(dataPsiFile);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());

    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("The forbidden schema selection must initially be cached as unresolved", service.getSchemaObject(dataPsiFile));

    TrustedProjects.setProjectTrusted(getProject(), true);
    assertNotNull("Granting trust must immediately invalidate the cached unresolved schema", service.getSchemaObject(dataPsiFile));
  }

  public void testRemoteSchemaRefIsNotResolvedWhenUntrusted() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    // A remote $schema declared in a project file: resolving it yields a (lazy) HttpVirtualFile which, once
    // JsonFileResolver.startFetchingHttpFileIfNeeded runs, fetches the attacker-controlled URL. This must not
    // happen before the user grants project trust.
    String reference = "http://schema.example.test/attacker.json";
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(getProject());

    TrustedProjects.setProjectTrusted(getProject(), false);
    assertNull("A remote schema referenced from a project file must not be resolved before the project is trusted",
               service.findSchemaFileByReference(reference, dataFile));

    // Trusted: resolution keeps working exactly as before the fix.
    TrustedProjects.setProjectTrusted(getProject(), true);
    VirtualFile remoteHandle = service.findSchemaFileByReference(reference, dataFile);
    if (remoteHandle != null) {
      assertFalse("A remote schema must resolve to a non-local handle", remoteHandle.isInLocalFileSystem());
    }
  }

  /** Touches the fixture so the JSON plugin content module (and its {@link JsonSchemaService}) is loaded. */
  private void warmUpJsonSchemaService() {
    myFixture.addFileToProject("warmup.json", "{}");
    assertNotNull("JsonSchemaService must be available", JsonSchemaService.Impl.get(getProject()));
  }

  private Path projectBaseDir() throws IOException {
    Path baseDir = Path.of(Objects.requireNonNull(getProject().getBasePath(), "project base path"));
    Files.createDirectories(baseDir);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), baseDir.toRealPath().getParent().toString());
    return baseDir;
  }

  private static VirtualFile findLocalFile(Path path) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
    assertNotNull("Expected file on disk: " + path, file);
    return file;
  }
}
