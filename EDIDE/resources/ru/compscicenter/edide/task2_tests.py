def test(a, b):
    if a == b:
        print "OK"
    else:
        print "not OK"

def test_task():
    test(me.match_ends(['aba', 'xyz', 'aa', 'x', 'bbb']), 3)
    test(me.match_ends(['', 'x', 'xy', 'xyx', 'xx']), 2)
    test(me.match_ends(['aaa', 'be', 'abc', 'hello']), 1)

if __name__ == '__main__':
    from sys import path
    import os.path
    parpath = os.path.abspath(os.path.join(os.getcwd(), os.pardir))
    task_path = os.path.join(parpath, "task2")
    path.append(task_path)
    import match_ends as me
    test_task()
