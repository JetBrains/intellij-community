class Test {
    {
        <selection>UrlClassLoader loader = UrlClassLoader.build().
          useCache(new UrlClassLoader.I() {
              @Override
              public void m() {}
          }).get();</selection>

        System.out.println(loader);
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