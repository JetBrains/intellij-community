class TextBlocks {
  static {
    System.out.println("""
                       first
                        second
                         third\
                       """ + <caret>//c1
                       " \\forth\"" );
  }
}