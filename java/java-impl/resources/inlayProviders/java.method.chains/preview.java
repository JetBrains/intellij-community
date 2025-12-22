class Test {
  static AccountManager accountManager = new AccountManager();

  public static void main(String[] args) {
    String id = accountManager
      .getAccounts()/*<# List<Account> #>*/
      .get(0)/*<# Account #>*/
      .getId()/*<# UUID> #>*/
      .toString();
  }

}

class AccountManager {
  List<Account> accounts = new ArrayList<>();

  public List<Account> getAccounts() {
    return accounts;
  }
}

class Account {
  private UUID id;

  public UUID getId() {
    return id;
  }
}