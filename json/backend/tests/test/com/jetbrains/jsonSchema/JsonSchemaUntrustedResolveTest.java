// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.ide.TrustedFiles;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TrustedProjectsTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * <p>
 * Also covers IJPL-254130: the same guard applies to a safe-mode file (see {@link TrustedFiles}) in a trusted project.
 */
@TestApplication
public class JsonSchemaUntrustedResolveTest {
  private final TestFixture<Project> projectFixture = codeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

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

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    TrustedProjectsTestUtil.enableTrustedProjectsCheck(myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() throws IOException {
    // Reset the explicit trust state that the test may have set on the project.
    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    JsonSchemaMappingsProjectConfiguration.getInstance(myFixture.getProject()).setState(Collections.emptyMap());
    JsonSchemaService.Impl.get(myFixture.getProject()).reset();
    for (Path path : myPathsToDelete) {
      NioFiles.deleteRecursively(path);
    }
  }

  @Test
  public void testOutsideProjectSchemaIsNotResolvedWhenUntrusted() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);

    String reference = "../" + outsideDir.getFileName() + "/schema.json";
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());
    String schemaRealPath = schemaPath.toRealPath().toString();
    assertNull(LocalFileSystem.getInstance().findFileByPathIfCached(schemaRealPath), "Test precondition: the outside schema must not be in the VFS cache");

    // Untrusted: the file outside the project must not be resolved (and therefore not read).
    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(reference, dataFile)), "A schema outside the project must not be resolved before the project is trusted");
    assertNull(LocalFileSystem.getInstance().findFileByPathIfCached(schemaRealPath), "Resolving a forbidden schema must not add it to the VFS cache");

    // Trusted: resolution keeps working exactly as before the fix.
    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference(reference, dataFile)), "A schema outside the project must resolve once the project is trusted");
  }

  @Test
  public void testInProjectSymlinkToOutsideSchemaIsNotResolvedWhenUntrusted() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-symlink-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    Path schemaLink = Files.createSymbolicLink(baseDir.resolve("schema-link.json"), schemaPath);
    myPathsToDelete.add(schemaLink);
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(schemaLink.getFileName()));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(schemaLink.getFileName().toString(), dataFile)), "An in-project symlink to an outside schema must not be resolved before the project is trusted");

    VirtualFile schemaFile = findLocalFile(schemaLink);
    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference(schemaLink.getFileName().toString(), dataFile)), "A symlinked schema must resolve once the project is trusted");
  }

  @Test
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
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    // A relative reference that stays inside the project must keep resolving even in an untrusted project.
    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference("schemas/schema.json", dataFile)), "An in-project schema must resolve regardless of project trust");
  }

  @Test
  public void testExternalContentRootDoesNotExpandUntrustedProjectBoundary() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path externalRootPath = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-external-root"));
    myPathsToDelete.add(externalRootPath);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), externalRootPath.toString());
    Path schemaPath = Files.writeString(externalRootPath.resolve("schema.json"), SCHEMA_TEXT);
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(schemaPath.toUri()));
    myPathsToDelete.add(dataPath);

    VirtualFile externalRoot = findLocalFile(externalRootPath);
    PsiTestUtil.addContentRoot(myFixture.getModule(), externalRoot);
    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(schemaPath.toUri().toString(), dataFile)), "A project-declared external content root must not expand the untrusted project boundary");

    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference(schemaPath.toUri().toString(), dataFile)), "The external content-root schema must resolve once the project is trusted");
  }

  @Test
  public void testProjectMappingOutsideProjectDoesNotBypassReferenceGuard() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-mapped-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), MAPPED_SCHEMA_TEXT);
    Path dataPath = Files.writeString(baseDir.resolve("mapped-data.json"), DATA_TEXT_TEMPLATE.formatted(MAPPED_SCHEMA_ID));
    myPathsToDelete.add(dataPath);

    UserDefinedJsonSchemaConfiguration.Item dataMapping =
      new UserDefinedJsonSchemaConfiguration.Item("mapped-data.json", JsonMappingKind.File);
    UserDefinedJsonSchemaConfiguration mapping =
      new UserDefinedJsonSchemaConfiguration("outside", JsonSchemaVersion.SCHEMA_7, schemaPath.toString(), false, List.of(dataMapping));
    JsonSchemaMappingsProjectConfiguration.getInstance(myFixture.getProject()).setState(Map.of(mapping.getName(), mapping));
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());
    service.reset();
    VirtualFile dataFile = findLocalFile(dataPath);
    PsiFile dataPsiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(dataFile));
    assertNotNull(dataPsiFile);

    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(MAPPED_SCHEMA_ID, dataFile)), "A project mapping outside the project must not resolve by its $id before trust is granted");
    assertNull(ReadAction.computeBlocking(() -> service.getSchemaObject(dataPsiFile)), "A project mapping outside the project must not be selected before trust is granted");

    VirtualFile schemaFile = findLocalFile(schemaPath);
    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference(MAPPED_SCHEMA_ID, dataFile)), "The mapped schema must resolve by its $id once the project is trusted");
    assertNotNull(ReadAction.computeBlocking(() -> service.getSchemaObject(dataPsiFile)), "The mapped schema must be selected once the project is trusted");
  }

  @Test
  public void testTrustChangeInvalidatesCachedUnresolvedSchema() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-cached-outside"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    String reference = "../" + outsideDir.getFileName() + "/schema.json";
    Path dataPath = Files.writeString(baseDir.resolve("cached-data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    PsiFile dataPsiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(dataFile));
    assertNotNull(dataPsiFile);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.getSchemaObject(dataPsiFile)), "The forbidden schema selection must initially be cached as unresolved");

    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertNotNull(ReadAction.computeBlocking(() -> service.getSchemaObject(dataPsiFile)), "Granting trust must immediately invalidate the cached unresolved schema");
  }

  @Test
  public void testRemoteSchemaRefIsNotResolvedWhenUntrusted() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    // A remote $schema declared in a project file must not be resolved before the user grants project trust.
    String reference = "http://schema.example.test/attacker.json";
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted(reference));
    myPathsToDelete.add(dataPath);

    VirtualFile dataFile = findLocalFile(dataPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), false);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(reference, dataFile)), "A remote schema referenced from a project file must not be resolved before the project is trusted");
  }

  @Test
  @RegistryKey(key = TrustedFiles.SAFE_MODE_REGISTRY_KEY, value = "true")
  public void testSafeModeFileInTrustedProjectDoesNotResolveRemoteOrOutsideSchema() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-safe-mode"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path schemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    String remoteReference = "http://schema.example.test/attacker.json";
    Path remoteDataPath = Files.writeString(outsideDir.resolve("remote-data.json"), DATA_TEXT_TEMPLATE.formatted(remoteReference));
    Path localDataPath = Files.writeString(outsideDir.resolve("local-data.json"), DATA_TEXT_TEMPLATE.formatted("schema.json"));

    VirtualFile remoteDataFile = findLocalFile(remoteDataPath);
    VirtualFile localDataFile = findLocalFile(localDataPath);
    TrustedFiles.markExternallyOpened(remoteDataFile);
    TrustedFiles.markExternallyOpened(localDataFile);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertFalse(TrustedFiles.isTrusted(localDataFile, myFixture.getProject()), "Test precondition: the marked outside file must be in the safe mode");
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference(remoteReference, remoteDataFile)), "A remote schema referenced from a safe-mode file must not resolve in a trusted project");
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference("schema.json", localDataFile)), "An outside-project schema referenced from a safe-mode file must not resolve in a trusted project");

    // Cache the unresolved selection: the trust grant below must invalidate it through the path-only event.
    PsiFile localDataPsiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(localDataFile));
    assertNotNull(localDataPsiFile);
    assertNull(ReadAction.computeBlocking(() -> service.getSchemaObject(localDataPsiFile)), "The forbidden schema selection must initially be cached as unresolved");

    // Trusting the file location lifts the safe mode; resolution works again.
    TrustedProjects.setProjectTrusted(localDataPath, true);
    VirtualFile schemaFile = findLocalFile(schemaPath);
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference("schema.json", localDataFile)), "The outside schema must resolve once the file location is trusted");
    assertNotNull(ReadAction.computeBlocking(() -> service.getSchemaObject(localDataPsiFile)), "Trusting the file location must immediately invalidate the cached unresolved schema");
  }

  @Test
  @RegistryKey(key = TrustedFiles.SAFE_MODE_REGISTRY_KEY, value = "true")
  public void testInProjectReferentIsNotRestrictedByMarkedOutsideFile() throws IOException {
    warmUpJsonSchemaService();
    Path baseDir = projectBaseDir();
    Path outsideDir = Files.createDirectories(baseDir.resolveSibling(baseDir.getFileName() + "-safe-mode-neighbor"));
    myPathsToDelete.add(outsideDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), outsideDir.toString());
    Path outsideSchemaPath = Files.writeString(outsideDir.resolve("schema.json"), SCHEMA_TEXT);
    Path outsideDataPath = Files.writeString(outsideDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted("schema.json"));
    Path schemaDir = Files.createDirectories(baseDir.resolve("schemas"));
    myPathsToDelete.add(schemaDir);
    Path schemaPath = Files.writeString(schemaDir.resolve("schema.json"), SCHEMA_TEXT);
    Path dataPath = Files.writeString(baseDir.resolve("data.json"), DATA_TEXT_TEMPLATE.formatted("schemas/schema.json"));
    myPathsToDelete.add(dataPath);

    VirtualFile outsideDataFile = findLocalFile(outsideDataPath);
    TrustedFiles.markExternallyOpened(outsideDataFile);
    VirtualFile dataFile = findLocalFile(dataPath);
    VirtualFile schemaFile = findLocalFile(schemaPath);
    JsonSchemaService service = JsonSchemaService.Impl.get(myFixture.getProject());

    TrustedProjects.setProjectTrusted(myFixture.getProject(), true);
    assertNull(ReadAction.computeBlocking(() -> service.findSchemaFileByReference("schema.json", outsideDataFile)), "Test precondition: the marked outside file must stay restricted");
    assertEquals(schemaFile, ReadAction.computeBlocking(() -> service.findSchemaFileByReference("schemas/schema.json", dataFile)), "An in-project referent must keep resolving an in-project schema");
    String outsideReference = "../" + outsideDir.getFileName() + "/schema.json";
    assertEquals(findLocalFile(outsideSchemaPath), ReadAction.computeBlocking(() -> service.findSchemaFileByReference(outsideReference, dataFile)), "An in-project referent in a trusted project must keep resolving a schema outside the project");
  }

  /** Touches the fixture so the JSON plugin content module (and its {@link JsonSchemaService}) is loaded. */
  private void warmUpJsonSchemaService() {
    myFixture.addFileToProject("warmup.json", "{}");
    assertNotNull(JsonSchemaService.Impl.get(myFixture.getProject()), "JsonSchemaService must be available");
  }

  private Path projectBaseDir() throws IOException {
    Path baseDir = Path.of(Objects.requireNonNull(myFixture.getProject().getBasePath(), "project base path"));
    Files.createDirectories(baseDir);
    VfsRootAccess.allowRootAccess(myFixture.getTestRootDisposable(), baseDir.toRealPath().getParent().toString());
    return baseDir;
  }

  private static VirtualFile findLocalFile(Path path) {
    VirtualFile file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path);
    assertNotNull(file, "Expected file on disk: " + path);
    return file;
  }
}
