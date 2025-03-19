class TextBlocks {
  static {
    System.out.println("""
                       first  \s\
                       second
                         third""" + <caret>//c1
                       """
                          _no space
                          """ );
  }
}