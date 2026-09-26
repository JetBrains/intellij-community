public class Test {
    void f() {
        readTable("im_amortizacao", connection);
// readTable("po_proc_valor", connection);
        <caret>readTable("kutilizadores", connection);
// readTable("po_proc_valor", connection);
    }
}