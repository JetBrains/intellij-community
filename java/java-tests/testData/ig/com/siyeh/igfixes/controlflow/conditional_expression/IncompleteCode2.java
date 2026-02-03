class X {

    void x(String mockedUrl, String url) {
      String s = mockedUrl == null <caret>|| mockedUrl.equals(url) ? submit(() -> content) : ;
    }

    String submit(Runnable r) {
      return "";
    }
}