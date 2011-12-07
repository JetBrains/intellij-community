package com.intellij.compiler;


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
public class ResourcePatternsTest extends LightCodeInsightFixtureTestCase {
  String[] oldPatterns

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
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

  public void testFilePattern() {
    conf.addResourceFilePattern('*.ttt')

    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('foo/bar.xxx'))
  }

  public void testWildcards() {
    conf.addResourceFilePattern('*.t?t')
    assert conf.isResourceFile(createFile('foo/bar.txt'))
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert conf.isResourceFile(createFile('foo/bar.tyt'))
    assert !conf.isResourceFile(createFile('foo/bar.xml'))
  }

  public void testDirectory() {
    conf.addResourceFilePattern('*/foo/*')
    assert conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert !conf.isResourceFile(createFile('foo/bar.ttt'))
  }

  public void testRootDirectory() {
    conf.addResourceFilePattern('foo/*')
    assert !conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert !conf.isResourceFile(createFile('goo/foo/zzz'))
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
  }

  public void testDoubleAsterisk() {
    conf.addResourceFilePattern('**/foo/*')
    assert conf.isResourceFile(createFile('foo/bar.ttt'))
    assert conf.isResourceFile(createFile('goo/foo/bar.ttt'))
    assert !conf.isResourceFile(createFile('goo/foo.zzz'))
    assert conf.isResourceFile(createFile('goo/foo/zzz'))
  }

  public void testResourceRoot() {
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
