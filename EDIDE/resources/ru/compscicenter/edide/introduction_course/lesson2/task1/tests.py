from test_helper import run_common_tests, failed, passed, import_task_file


def test_value():
    file = import_task_file()

    if file.greetings == "greetings":
        failed("You should assign different value to the variable")
    else:
        passed()

if __name__ == '__main__':
    test_value()
    run_common_tests("You should redefine variable 'greetings'")
