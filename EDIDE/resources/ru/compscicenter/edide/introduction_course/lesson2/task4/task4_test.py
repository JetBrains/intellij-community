from test_helper import run_common_tests, import_file


def test_division(path):
    file = import_file(path)
    if file.division == 4.5:
        return "Bravo"
    return "Please, reload the task and try again."

def test_remainder(path):
    file = import_file(path)
    if file.remainder == 1.0:
        return "Bravo"
    return "Please, reload the task and try again."


if __name__ == '__main__':
    run_common_tests('''number = 9.0

division = divide 'number' by two

remainder = calculate the remainder
''', '''number = 9.0

division =

remainder =
''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_division(path)