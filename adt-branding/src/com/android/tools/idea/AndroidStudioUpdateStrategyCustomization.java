package com.android.tools.idea;

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioUpdateStrategyCustomization extends UpdateStrategyCustomization {
  @Override
  public boolean forceEapUpdateChannelForEapBuilds() {
    return false;
  }

  @Override
  public boolean isChannelActive(@NotNull ChannelStatus channel) {
    return true;
  }

  @Override
  public boolean haveSameMajorVersion(@NotNull BuildNumber build1, @NotNull BuildNumber build2) {
    return androidStudioVersion(build1) == androidStudioVersion(build2);
  }

  @Override
  public boolean isNewerVersion(@NotNull BuildNumber candidateBuild, @NotNull BuildNumber currentBuild) {
    if (androidStudioVersion(candidateBuild) == androidStudioVersion(currentBuild)) {
      return super.isNewerVersion(candidateBuild, currentBuild);
    } else {
      return androidStudioVersion(candidateBuild) > androidStudioVersion(currentBuild);
    }
  }

  private static int androidStudioVersion(BuildNumber buildNumber) {
    return buildNumber.getComponents()[3];
  }
}
