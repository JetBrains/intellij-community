class NoNPE {

  void m(String[][] ss) {
      String[] strings = (ss = new String[][]{})[0];
      for (int i = 0, stringsLength = strings.length; i < stringsLength; i++) {
          String s = strings[i];

      }
  }
}