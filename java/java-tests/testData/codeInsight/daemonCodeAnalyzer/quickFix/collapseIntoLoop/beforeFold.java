// "Collapse into loop" "true"
class X {
  public int hashCode() {
    int result = super.hashCode();
    <selection>result = 31 * result + (field != null ? field.hashCode() : 0);
    result = 31 * result + (field2 != null ? field2.hashCode() : 0);
    result = 31 * result + (field3 != null ? field3.hashCode() : 0);
    result = 31 * result + (field4 != null ? field4.hashCode() : 0);
    result = 31 * result + (field5 != null ? field5.hashCode() : 0);</selection>
    return result;
  }
}