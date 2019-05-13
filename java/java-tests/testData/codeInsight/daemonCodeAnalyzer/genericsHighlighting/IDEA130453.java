
final class Example {

  static <D extends PayRunDetails<D>> void wild(final Payroll<?,?,D> payroll) {
    // just an ide error
    bound(payroll);

    final PayRun<?,?,D> payRun = payroll.getPayRun();
    final Payroll<?,?,?> payroll1 = payRun.getPayroll();
    final D details = payRun.getDetails();
    payRun.updateDetails(details);
  }

  static <P extends Payroll<P,PR,D>, PR extends PayRun<P,PR,D>, D extends PayRunDetails<D>> void bound(final Payroll<P,PR,D> payroll) {
    final PR payRun = payroll.getPayRun();
    final P payroll1 = payRun.getPayroll();
    final D details = payRun.getDetails();
    payRun.updateDetails(details);
  }

  interface PayRunDetails<D extends PayRunDetails<D>> {}

  interface Payroll<P extends Payroll<P,PR,D>, PR extends PayRun<P,PR,D>, D extends PayRunDetails<D>> {
    PR getPayRun();
  }

  interface PayRun<P extends Payroll<P,PR,D>, PR extends PayRun<P,PR,D>, D extends PayRunDetails<D>> {
    P getPayroll();

    D getDetails();

    void updateDetails(D value);
  }
}