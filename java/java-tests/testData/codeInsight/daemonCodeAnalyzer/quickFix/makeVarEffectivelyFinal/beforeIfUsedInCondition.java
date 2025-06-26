// "Make 'companies' effectively final by moving initializer to the 'if' statement" "false"
import java.util.List;

class Abc {

  private void method(X x, List<Integer> list) {
    var companies = x.getCompanies();
    if (companies.isEmpty()) {
      companies = List.of(x.getCompanyId());
    }
    list.forEach(w -> something(comp<caret>anies));
  }

  private void something(List<Integer> companies) {
  }

  static public class X {

    private int companyId;

    private List<Integer> companies;

    public int getCompanyId() {
      return companyId;
    }

    public List<Integer> getCompanies() {
      return companies;
    }
  }
}