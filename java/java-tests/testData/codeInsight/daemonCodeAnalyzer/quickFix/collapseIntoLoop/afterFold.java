// "Collapse into loop" "true"
class X {
  public int hashCode() {
    int result = super.hashCode();
      for (int i : new int[]{field != null ? field.hashCode() : 0, (field2 != null) ? field2.hashCode() : 0, field3 != null ? field3.hashCode() : 0, field4 != null ? field4.hashCode() : 0, field5 != null ? field5.hashCode() : 0}) {
          result = 31 * result + (i);
      }
      return result;
  }
}