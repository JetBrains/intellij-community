class PatternVariables {

  public static void main(String[] args) {
    for (String arg : args) { // arg unused

    }
    try (InputStream in = new FileInputStream("asdf")) { // in unused

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) { // no warning on exception parameter

    }
    final Object arg = args[0];
    if (arg instanceof String s) { // s unused

    }
    var strings = getStrings(); // strings unused
    final boolean ignored = new java.io.File(args[1]).delete(); // don't warn on variables named 'ignored'
  }
}