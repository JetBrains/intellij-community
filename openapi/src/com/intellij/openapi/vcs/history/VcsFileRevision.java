/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.history;

import java.util.Date;

public interface VcsFileRevision extends VcsFileContent {
  VcsFileRevision NULL = new VcsFileRevision() {
    public VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }

    public Date getRevisionDate() {
      return new Date();
    }

    public String getAuthor() {
      return "";
    }

    public String getCommitMessage() {
      return "";
    }

    public String getBranchName() {
      return null;
    }

    public void loadContent(){
    }

    public byte[] getContent(){
      return new byte[0];
    }

    public int compareTo(VcsFileRevision vcsFileRevision) {
      return 0;
    }
  };

  VcsRevisionNumber getRevisionNumber();
  String getBranchName();
  Date getRevisionDate();
  String getAuthor();
  String getCommitMessage();

}
