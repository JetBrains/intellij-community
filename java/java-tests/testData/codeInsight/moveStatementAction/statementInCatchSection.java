class StatementInCatchSection {

    void x() {
        try {
        } catch (ClassNotFoundException e) {

        } catch (IOException e) {
            <caret>e.printStackTrace();
        } catch (Error e) {
        }

    }
}