package com.intellij.compiler


import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull

/**
 * @author peter
 */
class ResourcePatternsTest extends LightCodeInsightFixtureTestCase {
  String[] oldPatterns

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        contentEntry.sourceFolders.each { contentEntry.removeSourceFolder(it) }
        contentEntry.addSourceFolder(contentEntry.getFile().createChildDirectory(this, "aaa").createChildDirectory(this, "bbb"), false)
        super.configureModule(module, model, contentEntry)
      }
    }

  }

  @Override
  protected void setUp() {
    super.setUp()
    oldPatterns = conf.resourceFilePatterns
    conf.removeResourceFilePatterns()
  }

  @Override
  protected void tearDown() {
    conf.removeResourceFilePatterns()
    oldPatterns.each { conf.addResourceFilePattern(it) }
    super.tearDown()
  }

  void testFilePattern() {
    conf.addResourceFilePattern('*.ttt')

    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('foo/bar.xxx'))
  }

  void testWildcards() {
    conf.addResourceFilePattern('*.t?t')
    assert conf.isResourceFile(createFile('foo/bar.txt'))
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert conf.isResourceFile(createFile('foo/bar.tyt'))
    assert !conf.isResourceFile(createFile('foo/bar.xml'))
  }

  void testDirectory() {
    conf.addResourceFilePattern('*/foo/*')
    assert conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert !conf.isResourceFile(createFile('foo/bar.ttt'))
  }

  void testRootDirectory() {
    conf.addResourceFilePattern('foo/*')
    assert !conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert !conf.isResourceFile(createFile('goo/foo/zzz'))
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
  }

  void testDoubleAsterisk() {
    conf.addResourceFilePattern('**/foo/*')
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert conf.isResourceFile(createFile('goo/foo/zzz'))
  }

  void testResourceRoot() {
    conf.addResourceFilePattern('bbb:*.ttt')

    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('foo/bar.xxx'))
    assert !conf.isResourceFile(myFixture.addFileToProject("aaa/ccc/xxx.ttt", '').virtualFile)
  }

  private VirtualFile createFile(String path) {
    return myFixture.addFileToProject("aaa/bbb/" + path, '').virtualFile
  }

  private CompilerConfigurationImpl getConf() {
    return CompilerConfiguration.getInstance(project)
  }
}
