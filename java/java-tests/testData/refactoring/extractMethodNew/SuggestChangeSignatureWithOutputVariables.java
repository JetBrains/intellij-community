
class Test {

  private String getRevenue() {
    <selection>final String query = createNamedQuery("revenues");
    String revenues = "";
    final String revenue;
    revenue = "a";
    </selection>
    return revenue;
  }

  public String getExpense() {
    final String query = createNamedQuery("expenses");
    String expenses = "";
    final String expense;
    expense = "a";

    return expense;
  }

  private String createNamedQuery(String expenses) {
    return null;
  }
}