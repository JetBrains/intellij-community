class Test {
    {
        UrlClassLoader loader = newMethod();

        System.out.println(loader);
    }

    private UrlClassLoader newMethod() {
        return UrlClassLoader.build().
              useCache(new UrlClassLoader.I() {
                  @Override
                  public void m() {}
              }).get();
    }

    static class UrlClassLoader {
        static UrlClassLoader build() {return new UrlClassLoader();}
        UrlClassLoader useCache(I i) {
            return this;
        }

        interface I {
            void m();
        }

        UrlClassLoader get() {
            return this;
        }
    }
}