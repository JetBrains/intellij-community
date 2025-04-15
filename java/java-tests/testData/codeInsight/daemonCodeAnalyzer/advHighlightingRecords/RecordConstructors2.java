public class InnerRecordHolder {
  public record InnerRecord() {

    <error descr="Compact constructor access level cannot be more restrictive than the record access level ('public')">InnerRecord</error> {
      System.out.println();
    }
  }

  public static void main(String[] args) {
    final InnerRecord innerRecord = new InnerRecord();
  }
}