class X {

    void x(String mockedUrl, String url) {
      String s<caret>;
        if (mockedUrl == null || mockedUrl.equals(url)) s = submit(() -> content);
        else s =;
    }

    String submit(Runnable r) {
      return "";
    }
}