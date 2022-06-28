interface MyTest {
    void foo(boolean withCompletion, boolean withAppointments, Boolean inDelay, String teamIds, String userIds, String ticketIds, String search, Long displayFilterId, Integer pageNumber, Integer pageSize, Object sort);

    default void bar() {
        foo(true, true, true, null, null, "",  null, 1, <caret>null, null, null);
    }
}