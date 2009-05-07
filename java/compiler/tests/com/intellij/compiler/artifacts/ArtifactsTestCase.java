package com.intellij.compiler.artifacts;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.packaging.artifacts.ArtifactManager;

/**
 * @author nik
 */
public abstract class ArtifactsTestCase extends IdeaTestCase {

  protected ArtifactManager getArtifactManager() {
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  protected void setUpModule() {
  }
}
