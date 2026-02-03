import org.jetbrains.annotations.Nullable;

class Bar {

    void navigateTo(final@Nullable Object p) {
      if (p == null) {
        return;
      }

        Runnable c = new Runnable() {
            public void run() {
                p.hashCode();
            }
        };
    }

}