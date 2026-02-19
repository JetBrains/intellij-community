class StatementInCatchSection {

    void x() {
        try {
        } catch (ClassNotFoundException e) {

        } catch (IOException e) {
        } catch (Error e) {
            <caret>e.printStackTrace();
        }

    }
}