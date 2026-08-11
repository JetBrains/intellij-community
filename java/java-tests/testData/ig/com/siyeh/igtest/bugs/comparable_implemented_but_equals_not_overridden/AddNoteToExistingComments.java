/// Markdown
/// 
/// comment without tags
class <warning descr="Class 'Note' implements 'java.lang.Comparable' but does not override 'equals()'">Note</warning><caret> implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/// Markdown
///
/// comment with tags
/// 
/// @apiNote Very bad comparator
class <warning descr="Class 'Note1' implements 'java.lang.Comparable' but does not override 'equals()'">Note1</warning> implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/**
 * classic comment without tags
 */
class <warning descr="Class 'Note2' implements 'java.lang.Comparable' but does not override 'equals()'">Note2</warning> implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/**
 * classic comment without tags
 * 
 * @apiNote Very bad comparator
 */
class <warning descr="Class 'Note3' implements 'java.lang.Comparable' but does not override 'equals()'">Note3</warning> implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}
