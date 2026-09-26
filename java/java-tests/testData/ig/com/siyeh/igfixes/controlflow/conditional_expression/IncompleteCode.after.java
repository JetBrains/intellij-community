class X {

    String x(String mockedUrl, String url) {
        if (mockedUrl == null |<caret>| mockedUrl.equals(url)) return submit(() -> content);
    }

    String submit(Runnable r) {
      return "";
    }
}