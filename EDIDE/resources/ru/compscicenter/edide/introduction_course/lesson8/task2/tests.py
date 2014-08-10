from test_helper import run_common_tests, failed, passed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "my_object" in window and "variable1" in window:
        passed()
    else:
        failed("Access 'variable1' using my_object.variable1")

if __name__ == '__main__':
    run_common_tests()
    test_window()
