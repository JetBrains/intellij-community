package com.intellij.lang.cacheBuilder;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 31, 2005
 * Time: 9:09:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class WordOccurence {
  private Kind myKind;
  private CharSequence myText;

  public WordOccurence(final CharSequence text, final Kind kind) {
    myKind = kind;
    myText = text;
  }

  public Kind getKind() {
    return myKind;
  }

  public CharSequence getText() {
    return myText;
  }

  public static class Kind {
    public static final Kind CODE = new Kind("CODE");
    public static final Kind COMMENTS = new Kind("COMMENTS");
    public static final Kind LITERALS = new Kind("LITERALS");

    private String myName;

    private Kind(String name){
      myName = name;
    }

    public String toString() {
      return "WordOccurence.Kind(" + myName + ")";
    }
  }
}
