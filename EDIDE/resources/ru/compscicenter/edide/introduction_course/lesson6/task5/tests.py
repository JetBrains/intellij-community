from test_helper import run_common_tests, passed, failed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "x" in window and "%" in window:
        passed()
    else:
        failed("Use % operator to check that x is even")

if __name__ == '__main__':
    run_common_tests()
    test_window()