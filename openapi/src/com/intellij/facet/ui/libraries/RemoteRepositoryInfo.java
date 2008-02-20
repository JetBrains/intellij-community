package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author nik
 */
public class RemoteRepositoryInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.ui.libraries.RemoteRepositoryInfo");
  private String myId;
  private String myPresentableName;
  private String[] myMirrors;

  public RemoteRepositoryInfo(@NotNull @NonNls String id, final @NotNull @Nls String presentableName, final @NotNull @NonNls String[] mirrors) {
    myId = id;
    LOG.assertTrue(mirrors.length > 0);
    myPresentableName = presentableName;
    myMirrors = mirrors;
  }

  public String getId() {
    return myId;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public String[] getMirrors() {
    return myMirrors;
  }

  public String getDefaultMirror() {
    return myMirrors[0];
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RemoteRepositoryInfo that = (RemoteRepositoryInfo)o;
    return myId.equals(that.myId);

  }

  public int hashCode() {
    return myId.hashCode();
  }
}
