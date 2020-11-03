// "Merge nested 'if' statements" "true"

class Test {
  // IDEA-179557
  public static void main(String[] args) {
    long abc = 0;
    do {
        // comment
        if (abc++ == 71 && abc++ >= 999) {
            System.out.println(88);
            if (abc++ < 23) {
                System.err.println("Log nonsense");
            }
        }
    } while( abc++ < 7 );
    if ( abc++ < 47 ) {
      System.out.println(abc);
    }
  }
}
