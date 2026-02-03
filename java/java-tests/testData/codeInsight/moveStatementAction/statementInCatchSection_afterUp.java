class StatementInCatchSection {

    void x() {
        try {
        } catch (ClassNotFoundException e) {

            <caret>e.printStackTrace();
        } catch (IOException e) {
        } catch (Error e) {
        }

    }
}