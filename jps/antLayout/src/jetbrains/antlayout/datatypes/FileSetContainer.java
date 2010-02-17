package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.types.FileSet;

/**
 * @author nik
 */
public class FileSetContainer extends Container {
  public FileSetContainer(FileSet fileSet) {
    addFileset(fileSet);
  }
}
