package org.jetbrains.jps.incremental.messages;

import java.util.Collection;

/**
 * @author nik
 */
public class FileDeletedEvent extends BuildMessage {
  private Collection<String> myFilePaths;

  public FileDeletedEvent(Collection<String> filePaths) {
    super("", Kind.INFO);
    myFilePaths = filePaths;
  }

  public Collection<String> getFilePaths() {
    return myFilePaths;
  }
}
