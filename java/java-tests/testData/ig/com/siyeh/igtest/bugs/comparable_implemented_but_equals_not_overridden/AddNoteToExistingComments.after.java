/// Markdown
///
/// comment without tags
/// Note: this class has a natural ordering that is inconsistent with equals.
class Note implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/// Markdown
///
/// comment with tags
/// Note: this class has a natural ordering that is inconsistent with equals.
///
/// @apiNote Very bad comparator
class Note1 implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/**
 * classic comment without tags
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
class Note2 implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}

/**
 * classic comment without tags
 * Note: this class has a natural ordering that is inconsistent with equals.
 *
 * @apiNote Very bad comparator
 */
class Note3 implements Comparable<Note> {
  @Override
  public int compareTo(Note other) {
    return 0;
  }
}
