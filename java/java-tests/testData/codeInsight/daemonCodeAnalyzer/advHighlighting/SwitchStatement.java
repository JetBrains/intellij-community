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
      <error descr="Statement must be prepended with case label">System.out.println();</error>
    }

    switch (0) {
      <error descr="Statement must be prepended with case label">break;</error>
    }

    switch (0) {
      <error descr="Statement must be prepended with case label">return;</error>
    }
  }
}