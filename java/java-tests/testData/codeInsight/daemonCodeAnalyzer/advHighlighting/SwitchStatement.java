class SwitchStatement {
  void m() {
    {
      <error descr="Case statement outside switch">case 0:</error>
    }

    {
      <error descr="Case statement outside switch">default:</error>
    }

    switch (0) {
      ////////////////
      /** */
      <error descr="Statement must be prepended with a case label">System.out.println();</error>
    }

    switch (0) {
      <error descr="Statement must be prepended with a case label">break;</error>
    }

    switch (0) {
      <error descr="Statement must be prepended with a case label">return;</error>
    }

    switch (0) {
      case 0:
        class Local {}
      case 1:
        <error descr="Local class 'Local' cannot be referenced from another switch branch">Local</error> x = new <error descr="Local class 'Local' cannot be referenced from another switch branch">Local</error>();
    }
  }
  
  int unsupported(int i) {
    return <error descr="'switch' expressions are not supported at language level '1.4'">switch</error> (i) {
      default:
        throw new IllegalStateException("Unexpected value: " + i);
    };
  }
}