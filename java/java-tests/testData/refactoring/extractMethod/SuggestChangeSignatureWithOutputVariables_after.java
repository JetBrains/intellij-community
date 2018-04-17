import org.jetbrains.annotations.NotNull;

class Test {

  private String getRevenue() {
      final String revenue = newMethod("revenues");

      return revenue;
  }

    @NotNull
    private String newMethod(String revenues2) {
        final String query = createNamedQuery(revenues2);
        String revenues = "";
        final String revenue;
        revenue = "a";
        return revenue;
    }

    public String getExpense() {
        final String expense = newMethod("expenses");

        return expense;
  }

  private String createNamedQuery(String expenses) {
    return null;
  }
}