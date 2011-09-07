%startTest Navbar navigation test

%startTest Docked NavBar
%toggle on ViewNavigationBar
%include ../include/project1Init.ijs
%include basicTest.ijs
%endTest


%startTest Floating NavBar
%toggle off ViewNavigationBar
%include ../include/project1Init.ijs
%include basicTest.ijs
%endTest

%endTest

