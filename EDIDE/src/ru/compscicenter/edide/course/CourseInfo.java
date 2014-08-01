package ru.compscicenter.edide.course;

/**
 * author: liana
 * data: 7/31/14.
 */
public class CourseInfo {
  private String myName;
  private String myAuthor;
  private String myDescription;
  public static CourseInfo INVALID_COURSE = new CourseInfo("", "", "");

  public CourseInfo(String name, String author, String description) {
    myName = name;
    myAuthor = author;
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public String getDescription() {
    return myDescription;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CourseInfo that = (CourseInfo)o;

    if (myAuthor != null ? !myAuthor.equals(that.myAuthor) : that.myAuthor != null) return false;
    if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) return false;
    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myAuthor != null ? myAuthor.hashCode() : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }
}
