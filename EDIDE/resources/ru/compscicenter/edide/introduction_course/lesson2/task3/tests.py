from test_helper import run_common_tests, get_task_windows, passed, failed


def test_type_used():
    window = get_task_windows()[0]
    if "type" in window and "float_number" in window:
        passed()
    else:
        failed("Use type() function")

if __name__ == '__main__':
    run_common_tests("You should redefine variable 'greetings'")
    test_type_used()