package com.intellij.compiler;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author peter
 */
public class ResourcePatternsTest extends LightCodeInsightFixtureTestCase {
  String[] oldPatterns

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

  private VirtualFile createFile(String path) {
    return myFixture.addFileToProject(path, '').virtualFile
  }

  private CompilerConfigurationImpl getConf() {
    return CompilerConfiguration.getInstance(project)
  }
}
