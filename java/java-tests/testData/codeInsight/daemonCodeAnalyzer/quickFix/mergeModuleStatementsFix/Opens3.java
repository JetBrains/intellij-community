module M {
    opens /*first*/ my.api to M4;
    opens /*second*/ <caret>my.api to M2;
    opens /*third*/ my.api to M6;
}