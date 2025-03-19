class Complex2 {

  /**
   * https://youtrack.jetbrains.com/issue/IDEA-114997#comment=27-605057
   */
  void a(Repository repository, Integer workingCopyId) {
    final StringBuilder <caret>builder = (new StringBuilder());
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes"));
    builder.append(System.lineSeparator());
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes/_"));
    builder.append(System.lineSeparator());
    builder.append(executeAndDumpGetRequest("/repositories/" + toPathComponent(repository.name()) + "/workingCopies/" + toPathComponent(workingCopyId.toString()) + "/changes/__"));
    checkOutputFiles(new TestDataFile[]{new TestDataFile(testName(), builder.toString())});
  }

  private void checkOutputFiles(TestDataFile[] testDataFiles) {

  }

  private String executeAndDumpGetRequest(String s) {
    return s;
  }

  private String toPathComponent(String name) {
    return name;
  }

  public String testName() {
    return "test";
  }

  class TestDataFile {

    public TestDataFile(String s, String s1) {
      if (s == null && s == null) {}
    }
  }

  class Repository {

    public String name() {
      return "name";
    }
  }

  static class System {

    public static String lineSeparator() {
      return "\n";
    }
  }
}