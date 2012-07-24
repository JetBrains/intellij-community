class AAA {
  void fff(Project myProject) {
    ensureFilesWritable(myProject, new String[1]);
  }

  private boolean ensu<caret>reFilesWritable(final Project project, final String... strings) {
    return !ensureFilesWritable(strings).hasReadonlyFiles();
  }

  private Status ensureFilesWritable(final String[] strings) {
    return new Status(strings);
  }

  class Status {
    public Status(final String[] strings) {
      //To change body of created methods use File | Settings | File Templates.
    }

    boolean hasReadonlyFiles() {
      return true;
    }
  }

  class Project {
  }
}