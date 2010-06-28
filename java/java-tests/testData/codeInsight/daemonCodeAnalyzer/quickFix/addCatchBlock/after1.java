// "Add Catch Clause(s)" "true"
class a {
    void g() throws Exception {
    }
    void f() {
        try {
            g();
        } catch (Error e) {
        } catch (Exception e) {
            <caret><selection>e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.</selection>
        }
    }
}
