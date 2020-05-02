import pkg.Foo;

import java.io.Writer;

class Impl extends Abstract {
  Abstract orig;

    @Override
    public @Foo Writer getWriter() {
        return orig.getWriter();
    }
}
